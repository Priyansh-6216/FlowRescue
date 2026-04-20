package com.flowrescue.compensation.service;

import com.flowrescue.common.dto.CompensationEvent;
import com.flowrescue.common.enums.WorkflowStatus;
import com.flowrescue.common.model.AuditEvent;
import com.flowrescue.common.model.WorkflowExecution;
import com.flowrescue.compensation.handler.CompensationHandler;
import com.flowrescue.compensation.handler.CompensationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CompensationService {

    private final Map<String, CompensationHandler> handlers = new LinkedHashMap<>();
    private final DynamoDbEnhancedClient enhancedClient;

    public CompensationService(List<CompensationHandler> handlerList,
                               DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        for (CompensationHandler h : handlerList) {
            handlers.put(h.stepName(), h);
        }
        log.info("CompensationService initialized with handlers: {}", handlers.keySet());
    }

    /**
     * Execute compensation in the reverse order specified in the event.
     * Each step is compensated atomically. Compensation itself is idempotent.
     */
    public void compensate(CompensationEvent event) {
        String workflowId = event.getWorkflowId();
        List<String> stepsToCompensate = event.getStepsToCompensate();

        log.info("[{}] Beginning compensation — steps (reverse order): {}",
            workflowId, stepsToCompensate);

        if (stepsToCompensate == null || stepsToCompensate.isEmpty()) {
            log.info("[{}] No steps to compensate — marking COMPENSATED directly", workflowId);
            markCompensated(workflowId, "No side-effect steps to reverse");
            return;
        }

        boolean allSucceeded = true;

        for (String stepName : stepsToCompensate) {
            CompensationHandler handler = handlers.get(stepName);

            if (handler == null) {
                log.warn("[{}] No compensation handler for step {} — skipping", workflowId, stepName);
                writeAudit(workflowId, "COMPENSATION_SKIP", stepName,
                    "No handler registered — step skipped");
                continue;
            }

            try {
                CompensationResult result = handler.compensate(workflowId);
                writeAudit(workflowId, "COMPENSATION_STEP_SUCCESS", stepName, result.getMessage());
                log.info("[{}] ✓ Compensated step: {}", workflowId, stepName);
            } catch (Exception e) {
                log.error("[{}] ✗ Compensation failed for step {}: {}", workflowId, stepName, e.getMessage());
                writeAudit(workflowId, "COMPENSATION_STEP_FAILED", stepName, e.getMessage());
                allSucceeded = false;
            }
        }

        if (allSucceeded) {
            markCompensated(workflowId, event.getReason());
        } else {
            markManualReview(workflowId, "Partial compensation failure — manual intervention required");
        }
    }

    private void markCompensated(String workflowId, String reason) {
        WorkflowExecution execution = executionTable().getItem(
            Key.builder().partitionValue(workflowId).build()
        );
        if (execution != null) {
            execution.setStatus(WorkflowStatus.COMPENSATED.name());
            execution.setCompensatedAt(Instant.now().toString());
            execution.setCompensationReason(reason);
            execution.setUpdatedAt(Instant.now().toString());
            executionTable().updateItem(execution);
        }
        writeAudit(workflowId, "WORKFLOW_COMPENSATED", null, "All side effects reversed: " + reason);
        log.info("[{}] 🔁 Workflow fully compensated", workflowId);
    }

    private void markManualReview(String workflowId, String reason) {
        WorkflowExecution execution = executionTable().getItem(
            Key.builder().partitionValue(workflowId).build()
        );
        if (execution != null) {
            execution.setStatus(WorkflowStatus.MANUAL_REVIEW.name());
            execution.setUpdatedAt(Instant.now().toString());
            executionTable().updateItem(execution);
        }
        writeAudit(workflowId, "COMPENSATION_PARTIAL_FAILURE", null, reason);
    }

    private void writeAudit(String workflowId, String eventType, String stepName, String message) {
        AuditEvent event = AuditEvent.builder()
            .workflowId(workflowId)
            .eventTime(Instant.now().toString() + "_" + System.nanoTime())
            .eventType(eventType)
            .stepName(stepName)
            .message(message)
            .actor("compensation-service")
            .build();
        auditTable().putItem(event);
    }

    private DynamoDbTable<WorkflowExecution> executionTable() {
        return enhancedClient.table("WorkflowExecution",
            TableSchema.fromBean(WorkflowExecution.class));
    }

    private DynamoDbTable<AuditEvent> auditTable() {
        return enhancedClient.table("AuditTrail",
            TableSchema.fromBean(AuditEvent.class));
    }
}
