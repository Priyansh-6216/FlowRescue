package com.flowrescue.compensation.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CancelPaymentInstructionHandler implements CompensationHandler {

    @Override
    public String stepName() { return "CREATE_PAYMENT_INSTRUCTION"; }

    @Override
    public CompensationResult compensate(String workflowId) {
        log.info("[{}] ↩ Compensating CREATE_PAYMENT_INSTRUCTION — cancelling payment instruction", workflowId);

        // In production: call payment service to void the instruction by workflowId reference
        // Idempotent: calling cancel on an already-cancelled instruction is a no-op
        simulateLatency(150);

        log.info("[{}] ✓ Payment instruction cancelled successfully", workflowId);
        return CompensationResult.builder()
            .stepName(stepName())
            .success(true)
            .message("Payment instruction cancelled — provider will not be paid")
            .build();
    }

    private void simulateLatency(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
