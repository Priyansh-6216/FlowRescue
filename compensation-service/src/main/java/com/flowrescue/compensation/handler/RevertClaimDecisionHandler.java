package com.flowrescue.compensation.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RevertClaimDecisionHandler implements CompensationHandler {

    @Override
    public String stepName() { return "PERSIST_CLAIM_DECISION"; }

    @Override
    public CompensationResult compensate(String workflowId) {
        log.info("[{}] ↩ Compensating PERSIST_CLAIM_DECISION — reverting adjudication record", workflowId);

        simulateLatency(100);

        // In production: update claim record status to VOIDED / REVERSED
        log.info("[{}] ✓ Claim decision reverted — status set to VOIDED", workflowId);
        return CompensationResult.builder()
            .stepName(stepName())
            .success(true)
            .message("Adjudication decision reverted — claim status set to VOIDED")
            .build();
    }

    private void simulateLatency(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
