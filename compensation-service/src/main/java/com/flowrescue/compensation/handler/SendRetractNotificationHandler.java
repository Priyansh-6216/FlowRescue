package com.flowrescue.compensation.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SendRetractNotificationHandler implements CompensationHandler {

    @Override
    public String stepName() { return "SEND_NOTIFICATION"; }

    @Override
    public CompensationResult compensate(String workflowId) {
        log.info("[{}] ↩ Compensating SEND_NOTIFICATION — sending retraction notice", workflowId);

        simulateLatency(60);

        // In production: send a correction/retraction email/SMS to member
        log.info("[{}] ✓ Retraction notification sent", workflowId);
        return CompensationResult.builder()
            .stepName(stepName())
            .success(true)
            .message("Compensating notification sent — member informed of reversal")
            .build();
    }

    private void simulateLatency(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
