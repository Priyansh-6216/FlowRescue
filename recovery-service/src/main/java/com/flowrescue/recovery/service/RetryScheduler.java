package com.flowrescue.recovery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowrescue.common.dto.FailureEvent;
import com.flowrescue.common.dto.WorkflowTaskEvent;
import com.flowrescue.common.enums.WorkflowStatus;
import com.flowrescue.common.model.RecoverySchedule;
import com.flowrescue.common.model.WorkflowExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Simulates EventBridge Scheduler behavior.
 * Stores scheduled retries in RecoverySchedule DynamoDB table and
 * fires them when their scheduled time arrives via a @Scheduled poller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetryScheduler {

    private static final long[] DELAY_INTERVALS_MINUTES = {15, 60, 360};

    private final DynamoDbEnhancedClient enhancedClient;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queues.workflow-task}")
    private String workflowTaskQueueUrl;

    /**
     * Schedules a delayed retry and returns the delay in minutes.
     */
    public long scheduleDelayedRetry(FailureEvent event) {
        // Pick delay based on retry count
        int retryCount = event.getRetryCount();
        int intervalIndex = Math.min(retryCount - 3, DELAY_INTERVALS_MINUTES.length - 1);
        intervalIndex = Math.max(0, intervalIndex);
        long delayMinutes = DELAY_INTERVALS_MINUTES[intervalIndex];

        Instant scheduledFor = Instant.now().plusSeconds(delayMinutes * 60);

        RecoverySchedule schedule = RecoverySchedule.builder()
            .workflowId(event.getWorkflowId())
            .scheduleId(UUID.randomUUID().toString())
            .scheduledFor(scheduledFor.toString())
            .reason("Delayed retry — step: " + event.getFailedStep() + ", retryCount: " + retryCount)
            .createdAt(Instant.now().toString())
            .processed(false)
            .build();

        scheduleTable().putItem(schedule);
        log.info("[{}] Delayed retry scheduled at {} ({} min)", event.getWorkflowId(), scheduledFor, delayMinutes);

        return delayMinutes;
    }

    /**
     * Polls the RecoverySchedule table every 30 seconds and fires
     * any scheduled retries whose time has arrived.
     * Simulates what EventBridge Scheduler does in production.
     */
    @Scheduled(fixedDelay = 30000)
    public void processDueSchedules() {
        try {
            Instant now = Instant.now();
            List<RecoverySchedule> due = scheduleTable().scan().items().stream()
                .filter(s -> !s.isProcessed())
                .filter(s -> Instant.parse(s.getScheduledFor()).isBefore(now))
                .collect(Collectors.toList());

            for (RecoverySchedule schedule : due) {
                log.info("[{}] Firing delayed retry (scheduled at {})",
                    schedule.getWorkflowId(), schedule.getScheduledFor());

                WorkflowExecution execution = executionTable().getItem(
                    Key.builder().partitionValue(schedule.getWorkflowId()).build()
                );

                if (execution == null || WorkflowStatus.SUCCESS.name().equals(execution.getStatus())) {
                    markProcessed(schedule);
                    continue;
                }

                // Re-dispatch to task-runner from the failed step
                WorkflowTaskEvent retryEvent = WorkflowTaskEvent.builder()
                    .workflowId(execution.getWorkflowId())
                    .workflowType(execution.getWorkflowType())
                    .fromStep(execution.getFailedStep() != null ? execution.getFailedStep() : execution.getCurrentStep())
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
                executionTable().updateItem(execution);

                publishToSqs(workflowTaskQueueUrl, retryEvent);
                markProcessed(schedule);
            }
        } catch (Exception e) {
            log.error("Error processing due recovery schedules: {}", e.getMessage());
        }
    }

    private void markProcessed(RecoverySchedule schedule) {
        schedule.setProcessed(true);
        scheduleTable().putItem(schedule);
    }

    private DynamoDbTable<RecoverySchedule> scheduleTable() {
        return enhancedClient.table("RecoverySchedule",
            TableSchema.fromBean(RecoverySchedule.class));
    }

    private DynamoDbTable<WorkflowExecution> executionTable() {
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
            log.error("Failed SQS publish: {}", e.getMessage());
        }
    }
}
