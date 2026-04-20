package com.flowrescue.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowrescue.api.dto.StartWorkflowRequest;
import com.flowrescue.api.dto.WorkflowResponse;
import com.flowrescue.api.repository.AuditRepository;
import com.flowrescue.api.repository.WorkflowRepository;
import com.flowrescue.api.websocket.WorkflowEventBroadcaster;
import com.flowrescue.common.dto.CompensationEvent;
import com.flowrescue.common.dto.WorkflowTaskEvent;
import com.flowrescue.common.enums.WorkflowStatus;
import com.flowrescue.common.model.AuditEvent;
import com.flowrescue.common.model.IdempotencyRecord;
import com.flowrescue.common.model.WorkflowExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final AuditRepository auditRepository;
    private final IdempotencyService idempotencyService;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final WorkflowEventBroadcaster broadcaster;

    @Value("${aws.sqs.queues.workflow-task}")
    private String workflowTaskQueueUrl;

    @Value("${aws.sqs.queues.audit}")
    private String auditQueueUrl;

    // ── Start Workflow ──────────────────────────────────────────────────────────

    public WorkflowResponse startWorkflow(String idempotencyKey, StartWorkflowRequest request) {
        // 1. Idempotency check
        Optional<IdempotencyRecord> existing =
            idempotencyService.checkAndReserve(idempotencyKey, request);

        if (existing.isPresent()) {
            IdempotencyRecord rec = existing.get();
            return WorkflowResponse.builder()
                .workflowId(rec.getWorkflowId())
                .status(rec.getStatus())
                .message("Idempotent replay — workflow already exists")
                .build();
        }

        // 2. Create workflow record
        String workflowId = "wf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Instant now = Instant.now();

        WorkflowExecution execution = WorkflowExecution.builder()
            .workflowId(workflowId)
            .workflowType("CLAIM_PROCESSING")
            .status(WorkflowStatus.CREATED.name())
            .currentStep("VALIDATE_MEMBER")
            .completedSteps(List.of())
            .claimId(request.getClaimId())
            .memberId(request.getMemberId())
            .providerId(request.getProviderId())
            .amount(request.getAmount())
            .diagnosisCode(request.getDiagnosisCode())
            .serviceDate(request.getServiceDate())
            .retryCount(0)
            .version(1L)
            .startedAt(now.toString())
            .updatedAt(now.toString())
            .ttl(now.plusSeconds(86400 * 30).getEpochSecond()) // 30 day TTL
            .build();

        workflowRepository.save(execution);

        // 3. Audit
        writeAudit(workflowId, "WORKFLOW_STARTED", null, null,
            WorkflowStatus.CREATED.name(), "Workflow created and queued", "workflow-api");

        // 4. Dispatch to task-runner via SQS
        WorkflowTaskEvent taskEvent = WorkflowTaskEvent.builder()
            .workflowId(workflowId)
            .workflowType("CLAIM_PROCESSING")
            .claimId(request.getClaimId())
            .memberId(request.getMemberId())
            .providerId(request.getProviderId())
            .amount(request.getAmount())
            .diagnosisCode(request.getDiagnosisCode())
            .serviceDate(request.getServiceDate())
            .retryAttempt(0)
            .build();

        publishToSqs(workflowTaskQueueUrl, taskEvent);

        // 5. Mark idempotency as in-progress with workflowId
        idempotencyService.markCompleted(idempotencyKey, workflowId,
            "{\"workflowId\":\"" + workflowId + "\",\"status\":\"RUNNING\"}");

        // 6. Broadcast via WebSocket
        broadcaster.broadcastWorkflowUpdate(toResponse(execution));

        log.info("Started workflow {} for claim {}", workflowId, request.getClaimId());

        return WorkflowResponse.builder()
            .workflowId(workflowId)
            .status(WorkflowStatus.RUNNING.name())
            .startedAt(now.toString())
            .build();
    }

    // ── Get Status ──────────────────────────────────────────────────────────────

    public WorkflowResponse getStatus(String workflowId) {
        return workflowRepository.findById(workflowId)
            .map(this::toResponse)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));
    }

    // ── List All ────────────────────────────────────────────────────────────────

    public List<WorkflowResponse> listAll() {
        return workflowRepository.findAll().stream()
            .map(this::toResponse)
            .sorted((a, b) -> {
                // Sort by startedAt descending
                if (a.getStartedAt() == null) return 1;
                if (b.getStartedAt() == null) return -1;
                return b.getStartedAt().compareTo(a.getStartedAt());
            })
            .toList();
    }

    // ── Force Recovery ──────────────────────────────────────────────────────────

    public WorkflowResponse forceRecover(String workflowId) {
        WorkflowExecution execution = workflowRepository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        String failedStep = execution.getFailedStep() != null
            ? execution.getFailedStep() : execution.getCurrentStep();

        // Re-dispatch from the failed step
        WorkflowTaskEvent taskEvent = WorkflowTaskEvent.builder()
            .workflowId(workflowId)
            .workflowType(execution.getWorkflowType())
            .fromStep(failedStep)
            .claimId(execution.getClaimId())
            .memberId(execution.getMemberId())
            .providerId(execution.getProviderId())
            .amount(execution.getAmount())
            .diagnosisCode(execution.getDiagnosisCode())
            .serviceDate(execution.getServiceDate())
            .retryAttempt(execution.getRetryCount())
            .build();

        execution.setStatus(WorkflowStatus.RUNNING.name());
        execution.setUpdatedAt(Instant.now().toString());
        workflowRepository.update(execution);

        publishToSqs(workflowTaskQueueUrl, taskEvent);
        writeAudit(workflowId, "FORCE_RECOVERY", null, "FAILED",
            "RUNNING", "Manual force recovery triggered", "workflow-api");

        broadcaster.broadcastWorkflowUpdate(toResponse(execution));
        return toResponse(execution);
    }

    // ── Manual Compensate ───────────────────────────────────────────────────────

    public WorkflowResponse manualCompensate(String workflowId) {
        WorkflowExecution execution = workflowRepository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        // Build compensation event with side-effecting completed steps in reverse
        List<String> stepsToCompensate = execution.getCompletedSteps().stream()
            .filter(s -> s.equals("CREATE_PAYMENT_INSTRUCTION")
                      || s.equals("PERSIST_CLAIM_DECISION")
                      || s.equals("SEND_NOTIFICATION"))
            .toList()
            .reversed();

        CompensationEvent compensationEvent = CompensationEvent.builder()
            .workflowId(workflowId)
            .stepsToCompensate(stepsToCompensate)
            .reason("Manual compensation triggered via API")
            .triggeredAt(Instant.now().toString())
            .build();

        // Publish to compensation-queue
        publishToSqs(workflowTaskQueueUrl.replace("workflow-task-queue", "compensation-queue"),
            compensationEvent);

        execution.setStatus(WorkflowStatus.COMPENSATING.name());
        execution.setUpdatedAt(Instant.now().toString());
        workflowRepository.update(execution);

        writeAudit(workflowId, "MANUAL_COMPENSATE", null, execution.getStatus(),
            WorkflowStatus.COMPENSATING.name(), "Manual compensation triggered", "workflow-api");

        broadcaster.broadcastWorkflowUpdate(toResponse(execution));
        return toResponse(execution);
    }

    // ── Replay From Step ─────────────────────────────────────────────────────────

    public WorkflowResponse replayFromStep(String workflowId, String fromStep) {
        WorkflowExecution execution = workflowRepository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        WorkflowTaskEvent taskEvent = WorkflowTaskEvent.builder()
            .workflowId(workflowId)
            .workflowType(execution.getWorkflowType())
            .fromStep(fromStep)
            .claimId(execution.getClaimId())
            .memberId(execution.getMemberId())
            .providerId(execution.getProviderId())
            .amount(execution.getAmount())
            .diagnosisCode(execution.getDiagnosisCode())
            .serviceDate(execution.getServiceDate())
            .retryAttempt(0)
            .build();

        execution.setStatus(WorkflowStatus.RUNNING.name());
        execution.setCurrentStep(fromStep);
        execution.setUpdatedAt(Instant.now().toString());
        workflowRepository.update(execution);

        publishToSqs(workflowTaskQueueUrl, taskEvent);
        writeAudit(workflowId, "REPLAY_FROM_STEP", fromStep, execution.getStatus(),
            "RUNNING", "Admin replay from step: " + fromStep, "workflow-api");

        broadcaster.broadcastWorkflowUpdate(toResponse(execution));
        return toResponse(execution);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void publishToSqs(String queueUrl, Object payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .build());
        } catch (Exception e) {
            log.error("Failed to publish to SQS queue {}: {}", queueUrl, e.getMessage());
            throw new RuntimeException("SQS publish failed", e);
        }
    }

    private void writeAudit(String workflowId, String eventType, String stepName,
                             String fromStatus, String toStatus, String message, String actor) {
        AuditEvent event = AuditEvent.builder()
            .workflowId(workflowId)
            .eventTime(Instant.now().toString() + "_" + System.nanoTime())
            .eventType(eventType)
            .stepName(stepName)
            .fromStatus(fromStatus)
            .toStatus(toStatus)
            .message(message)
            .actor(actor)
            .build();
        auditRepository.save(event);
    }

    public WorkflowResponse toResponse(WorkflowExecution e) {
        return WorkflowResponse.builder()
            .workflowId(e.getWorkflowId())
            .status(e.getStatus())
            .currentStep(e.getCurrentStep())
            .completedSteps(e.getCompletedSteps())
            .retryCount(e.getRetryCount())
            .nextRetryAt(e.getNextRetryAt())
            .startedAt(e.getStartedAt())
            .updatedAt(e.getUpdatedAt())
            .lastErrorCode(e.getLastErrorCode())
            .lastErrorMessage(e.getLastErrorMessage())
            .failedStep(e.getFailedStep())
            .compensationReason(e.getCompensationReason())
            .build();
    }
}
