package com.flowrescue.taskrunner.step;

import com.flowrescue.common.model.WorkflowExecution;
import com.flowrescue.taskrunner.config.FailureInjectionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationStep implements StepHandler {

    private final FailureInjectionConfig failureConfig;

    @Override
    public String stepName() { return "SEND_NOTIFICATION"; }

    @Override
    public StepResult execute(WorkflowExecution execution) throws StepException {
        log.info("[{}] Executing SEND_NOTIFICATION for member={}", execution.getWorkflowId(), execution.getMemberId());

        if (failureConfig.shouldFail(stepName())) {
            // Notification failures are retryable but non-critical (compensate = send correction notice)
            throw new StepException("NOTIFICATION_DELIVERY_FAILED",
                "Notification service unavailable — will retry via SQS DLQ", true);
        }

        simulateLatency(60);

        log.info("[{}] SEND_NOTIFICATION ✓ — email/SMS sent to member {}", execution.getWorkflowId(), execution.getMemberId());

        return StepResult.builder()
            .stepName(stepName())
            .success(true)
            .resultPayload(String.format(
                "{\"channels\":[\"EMAIL\",\"SMS\"],\"memberId\":\"%s\",\"template\":\"CLAIM_APPROVED\",\"sentAt\":\"%s\"}",
                execution.getMemberId(), java.time.Instant.now()))
            .message("Notifications dispatched via email and SMS")
            .build();
    }

    private void simulateLatency(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
