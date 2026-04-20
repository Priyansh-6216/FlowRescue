package com.flowrescue.recovery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowrescue.common.dto.CompensationEvent;
import com.flowrescue.common.dto.FailureEvent;
import com.flowrescue.common.dto.WorkflowTaskEvent;
import com.flowrescue.common.enums.FailureDecision;
import com.flowrescue.common.enums.WorkflowStatus;
import com.flowrescue.common.model.WorkflowExecution;
import com.flowrescue.recovery.classifier.FailureClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryService {

    private final FailureClassifier classifier;
    private final RetryScheduler retryScheduler;
    private final SqsClient sqsClient;
    private final DynamoDbEnhancedClient enhancedClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queues.workflow-task}")
    private String workflowTaskQueueUrl;

    @Value("${aws.sqs.queues.compensation}")
    private String compensationQueueUrl;

    @Value("${aws.sqs.queues.manual-review}")
    private String manualReviewQueueUrl;

    public void handle(FailureEvent event) {
        FailureDecision decision = classifier.classify(event);
        log.info("[{}] Recovery decision: {}", event.getWorkflowId(), decision);

        switch (decision) {
            case RETRY_NOW    -> retryNow(event);
            case RETRY_LATER  -> retryLater(event);
            case COMPENSATE   -> compensate(event);
            case MANUAL_REVIEW -> escalateToManualReview(event);
            case FAIL_FINAL   -> markFinal(event);
        }
    }

    // ── RETRY_NOW ─────────────────────────────────────────────────────────────

    private void retryNow(FailureEvent event) {
        WorkflowExecution execution = loadExecution(event.getWorkflowId());
        if (execution == null) return;

        int newRetryCount = event.getRetryCount() + 1;
        execution.setRetryCount(newRetryCount);
        execution.setStatus(WorkflowStatus.RUNNING.name());
        execution.setUpdatedAt(Instant.now().toString());
        saveExecution(execution);

        WorkflowTaskEvent taskEvent = buildRetryTaskEvent(event, newRetryCount);
        publishToSqs(workflowTaskQueueUrl, taskEvent);

        log.info("[{}] RETRY_NOW dispatched — attempt #{}", event.getWorkflowId(), newRetryCount);
    }

    // ── RETRY_LATER ───────────────────────────────────────────────────────────

    private void retryLater(FailureEvent event) {
        WorkflowExecution execution = loadExecution(event.getWorkflowId());
        if (execution == null) return;

        long delayMinutes = retryScheduler.scheduleDelayedRetry(event);
        Instant scheduledFor = Instant.now().plusSeconds(delayMinutes * 60);

        execution.setRetryCount(event.getRetryCount() + 1);
        execution.setStatus(WorkflowStatus.RETRY_SCHEDULED.name());
        execution.setNextRetryAt(scheduledFor.toString());
        execution.setUpdatedAt(Instant.now().toString());
        saveExecution(execution);

        log.info("[{}] RETRY_LATER scheduled in {} minutes at {}", 
            event.getWorkflowId(), delayMinutes, scheduledFor);
    }

    // ── COMPENSATE ────────────────────────────────────────────────────────────

    private void compensate(FailureEvent event) {
        WorkflowExecution execution = loadExecution(event.getWorkflowId());
        if (execution == null) return;

        // Build list of steps to compensate in reverse order (only side-effecting steps)
        List<String> sideEffectSteps = List.of(
            "CREATE_PAYMENT_INSTRUCTION", "PERSIST_CLAIM_DECISION", "SEND_NOTIFICATION");

        List<String> stepsToCompensate = event.getCompletedSteps().stream()
            .filter(sideEffectSteps::contains)
            .toList()
            .reversed();

        CompensationEvent compensationEvent = CompensationEvent.builder()
            .workflowId(event.getWorkflowId())
            .stepsToCompensate(stepsToCompensate)
            .reason("Max retries exhausted after failure at step: " + event.getFailedStep())
            .triggeredAt(Instant.now().toString())
            .build();

        execution.setStatus(WorkflowStatus.COMPENSATING.name());
        execution.setCompensationReason(compensationEvent.getReason());
        execution.setUpdatedAt(Instant.now().toString());
        saveExecution(execution);

        publishToSqs(compensationQueueUrl, compensationEvent);
        log.info("[{}] COMPENSATE triggered — steps to undo: {}", 
            event.getWorkflowId(), stepsToCompensate);
    }

    // ── MANUAL_REVIEW ─────────────────────────────────────────────────────────

    private void escalateToManualReview(FailureEvent event) {
        WorkflowExecution execution = loadExecution(event.getWorkflowId());
        if (execution == null) return;

        execution.setStatus(WorkflowStatus.MANUAL_REVIEW.name());
        execution.setUpdatedAt(Instant.now().toString());
        saveExecution(execution);

        publishToSqs(manualReviewQueueUrl, event);
        log.info("[{}] MANUAL_REVIEW — escalated for human intervention", event.getWorkflowId());
    }

    // ── FAIL_FINAL ────────────────────────────────────────────────────────────

    private void markFinal(FailureEvent event) {
        WorkflowExecution execution = loadExecution(event.getWorkflowId());
        if (execution == null) return;

        execution.setStatus(WorkflowStatus.FAILED.name());
        execution.setLastErrorCode(event.getErrorCode());
        execution.setLastErrorMessage("Terminal failure: " + event.getErrorMessage());
        execution.setUpdatedAt(Instant.now().toString());
        saveExecution(execution);

        log.warn("[{}] FAIL_FINAL — terminal business failure, no recovery possible: {}",
            event.getWorkflowId(), event.getErrorCode());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkflowTaskEvent buildRetryTaskEvent(FailureEvent event, int retryAttempt) {
        WorkflowExecution execution = loadExecution(event.getWorkflowId());
        return WorkflowTaskEvent.builder()
            .workflowId(event.getWorkflowId())
            .workflowType("CLAIM_PROCESSING")
            .fromStep(event.getFailedStep())
            .claimId(execution != null ? execution.getClaimId() : null)
            .memberId(execution != null ? execution.getMemberId() : null)
            .providerId(execution != null ? execution.getProviderId() : null)
            .amount(execution != null ? execution.getAmount() : null)
            .diagnosisCode(execution != null ? execution.getDiagnosisCode() : null)
            .serviceDate(execution != null ? execution.getServiceDate() : null)
            .retryAttempt(retryAttempt)
            .build();
    }

    private WorkflowExecution loadExecution(String workflowId) {
        return table().getItem(Key.builder().partitionValue(workflowId).build());
    }

    private void saveExecution(WorkflowExecution execution) {
        table().updateItem(execution);
    }

    private DynamoDbTable<WorkflowExecution> table() {
        return enhancedClient.table("WorkflowExecution",
            TableSchema.fromBean(WorkflowExecution.class));
    }

    private void publishToSqs(String queueUrl, Object payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .build());
        } catch (Exception e) {
            log.error("Failed to publish to {}: {}", queueUrl, e.getMessage());
        }
    }
}
