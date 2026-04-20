package com.flowrescue.taskrunner.step;

import com.flowrescue.common.model.WorkflowExecution;
import com.flowrescue.taskrunner.config.FailureInjectionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentInstructionStep implements StepHandler {

    private final FailureInjectionConfig failureConfig;

    @Override
    public String stepName() { return "CREATE_PAYMENT_INSTRUCTION"; }

    @Override
    public StepResult execute(WorkflowExecution execution) throws StepException {
        log.info("[{}] Executing CREATE_PAYMENT_INSTRUCTION", execution.getWorkflowId());

        if (failureConfig.shouldFail(stepName())) {
            throw new StepException("PAYMENT_SERVICE_TIMEOUT",
                "Payment instruction service timed out — this step has side effects and requires compensation on repeated failure",
                true);
        }

        simulateLatency(200);

        // THIS STEP HAS SIDE EFFECTS — generates a real payment instruction record
        String paymentRef = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("[{}] CREATE_PAYMENT_INSTRUCTION ✓ — paymentRef={}", execution.getWorkflowId(), paymentRef);

        return StepResult.builder()
            .stepName(stepName())
            .success(true)
            .resultPayload(String.format(
                "{\"paymentRef\":\"%s\",\"providerId\":\"%s\",\"amount\":%.2f,\"status\":\"PENDING\"}",
                paymentRef, execution.getProviderId(), execution.getAmount()))
            .message("Payment instruction created: " + paymentRef)
            .build();
    }

    private void simulateLatency(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
