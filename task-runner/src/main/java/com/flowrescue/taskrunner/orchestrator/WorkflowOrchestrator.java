package com.flowrescue.taskrunner.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowrescue.common.dto.FailureEvent;
import com.flowrescue.common.dto.WorkflowTaskEvent;
import com.flowrescue.common.enums.WorkflowStatus;
import com.flowrescue.common.model.WorkflowExecution;
import com.flowrescue.taskrunner.step.*;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core workflow state machine.
 * Executes steps in order, tracks completedSteps in DynamoDB,
 * and publishes failure events to the recovery queue on error.
 *
 * This mirrors the ASL state machine in infra/statemachine/claim_workflow.asl.json
 * but runs entirely in Java for local execution.
 */
@Service
@Slf4j
public class WorkflowOrchestrator {

    // Ordered pipeline
    private static final List<String> STEP_ORDER = List.of(
        "VALIDATE_MEMBER",
        "CHECK_DUPLICATE_CLAIM",
        "CALCULATE_PAYABLE",
        "CREATE_PAYMENT_INSTRUCTION",
        "PERSIST_CLAIM_DECISION",
        "SEND_NOTIFICATION"
    );

    private final Map<String, StepHandler> stepHandlers = new LinkedHashMap<>();
    private final DynamoDbEnhancedClient enhancedClient;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queues.recovery}")
    private String recoveryQueueUrl;

    @Value("${aws.sqs.queues.audit}")
    private String auditQueueUrl;

    public WorkflowOrchestrator(
            List<StepHandler> handlers,
            DynamoDbEnhancedClient enhancedClient,
            SqsClient sqsClient,
            ObjectMapper objectMapper) {
        this.enhancedClient = enhancedClient;
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        for (StepHandler h : handlers) {
            stepHandlers.put(h.stepName(), h);
        }
        log.info("Orchestrator initialized with steps: {}", stepHandlers.keySet());
    }

    /**
     * Execute the workflow from the beginning or from a specific step (resume/replay).
     */
    public void execute(WorkflowTaskEvent event) {
        String workflowId = event.getWorkflowId();
        DynamoDbTable<WorkflowExecution> table = table();

        WorkflowExecution execution = table.getItem(
            Key.builder().partitionValue(workflowId).build()
        );

        if (execution == null) {
            log.error("No workflow record found for id={}", workflowId);
            return;
        }

        // Mark as RUNNING
        execution.setStatus(WorkflowStatus.RUNNING.name());
        execution.setUpdatedAt(Instant.now().toString());
        table.updateItem(execution);

        // Determine where to start
        String startStep = event.getFromStep() != null ? event.getFromStep() : STEP_ORDER.get(0);
        int startIndex = STEP_ORDER.indexOf(startStep);
        if (startIndex < 0) {
            log.error("Unknown startStep '{}' — aborting", startStep);
            return;
        }

        // Ensure completedSteps list is mutable
        List<String> completedSteps = new ArrayList<>(
            execution.getCompletedSteps() != null ? execution.getCompletedSteps() : List.of()
        );

        log.info("[{}] Starting orchestration from step {} (index {})", workflowId, startStep, startIndex);

        for (int i = startIndex; i < STEP_ORDER.size(); i++) {
            String stepName = STEP_ORDER.get(i);
            StepHandler handler = stepHandlers.get(stepName);

            if (handler == null) {
                log.warn("[{}] No handler registered for step {}", workflowId, stepName);
                continue;
            }

            // Update current step in DynamoDB
            execution.setCurrentStep(stepName);
            execution.setUpdatedAt(Instant.now().toString());
            table.updateItem(execution);

            try {
                StepResult result = handler.execute(execution);

                // Step succeeded — record in completedSteps
                if (!completedSteps.contains(stepName)) {
                    completedSteps.add(stepName);
                }
                execution.setCompletedSteps(completedSteps);
                execution.setUpdatedAt(Instant.now().toString());
                table.updateItem(execution);

                log.info("[{}] ✓ Step {} completed", workflowId, stepName);

            } catch (StepException ex) {
                log.error("[{}] ✗ Step {} failed — errorCode={} retryable={} message={}",
                    workflowId, stepName, ex.getErrorCode(), ex.isRetryable(), ex.getMessage());

                // Persist failure metadata
                execution.setStatus(WorkflowStatus.FAILED.name());
                execution.setFailedStep(stepName);
                execution.setLastErrorCode(ex.getErrorCode());
                execution.setLastErrorMessage(ex.getMessage());
                execution.setCompletedSteps(completedSteps);
                execution.setUpdatedAt(Instant.now().toString());
                table.updateItem(execution);

                // Publish to recovery queue for classification
                publishFailureEvent(workflowId, stepName, ex, completedSteps,
                    event.getRetryAttempt());
                return;

            } catch (Exception ex) {
                log.error("[{}] ✗ Step {} threw unexpected exception: {}", workflowId, stepName, ex.getMessage());

                execution.setStatus(WorkflowStatus.FAILED.name());
                execution.setFailedStep(stepName);
                execution.setLastErrorCode("UNEXPECTED_ERROR");
                execution.setLastErrorMessage(ex.getMessage());
                execution.setCompletedSteps(completedSteps);
                execution.setUpdatedAt(Instant.now().toString());
                table.updateItem(execution);

                publishFailureEvent(workflowId, stepName,
                    new StepException("UNEXPECTED_ERROR", ex.getMessage(), true),
                    completedSteps, event.getRetryAttempt());
                return;
            }
        }

        // All steps completed successfully
        execution.setStatus(WorkflowStatus.SUCCESS.name());
        execution.setCurrentStep("COMPLETED");
        execution.setCompletedSteps(completedSteps);
        execution.setUpdatedAt(Instant.now().toString());
        table.updateItem(execution);

        log.info("[{}] 🎉 Workflow completed successfully! Steps: {}", workflowId, completedSteps);
    }

    private void publishFailureEvent(String workflowId, String failedStep,
                                      StepException ex, List<String> completedSteps,
                                      int retryAttempt) {
        FailureEvent event = FailureEvent.builder()
            .workflowId(workflowId)
            .failedStep(failedStep)
            .errorCode(ex.getErrorCode())
            .errorMessage(ex.getMessage())
            .exceptionType(ex.isRetryable() ? "RetryableException" : "BusinessException")
            .retryCount(retryAttempt)
            .completedSteps(completedSteps)
            .occurredAt(Instant.now().toString())
            .build();

        try {
            String body = objectMapper.writeValueAsString(event);
            sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(recoveryQueueUrl)
                .messageBody(body)
                .build());
            log.info("[{}] Failure event published to recovery-queue", workflowId);
        } catch (Exception e) {
            log.error("[{}] Failed to publish failure event: {}", workflowId, e.getMessage());
        }
    }

    private DynamoDbTable<WorkflowExecution> table() {
        return enhancedClient.table("WorkflowExecution",
            TableSchema.fromBean(WorkflowExecution.class));
    }
}
