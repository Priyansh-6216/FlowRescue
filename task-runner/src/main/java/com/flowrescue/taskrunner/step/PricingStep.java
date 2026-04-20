package com.flowrescue.taskrunner.step;

import com.flowrescue.common.model.WorkflowExecution;
import com.flowrescue.taskrunner.config.FailureInjectionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PricingStep implements StepHandler {

    private final FailureInjectionConfig failureConfig;

    @Override
    public String stepName() { return "CALCULATE_PAYABLE"; }

    @Override
    public StepResult execute(WorkflowExecution execution) throws StepException {
        log.info("[{}] Executing CALCULATE_PAYABLE for amount={}", execution.getWorkflowId(), execution.getAmount());

        if (failureConfig.shouldFail(stepName())) {
            throw new StepException("PRICING_ENGINE_ERROR",
                "Pricing engine returned HTTP 503 — retryable", true);
        }

        simulateLatency(150);

        // Simulate pricing: apply 80% coverage, deduct $50 deductible
        double claimAmount = execution.getAmount() != null ? execution.getAmount() : 0.0;
        double deductible = 50.0;
        double coveredPercent = 0.80;
        double payable = Math.max(0, (claimAmount - deductible) * coveredPercent);

        log.info("[{}] CALCULATE_PAYABLE ✓ — payable=${}", execution.getWorkflowId(), String.format("%.2f", payable));

        return StepResult.builder()
            .stepName(stepName())
            .success(true)
            .resultPayload(String.format(
                "{\"claimAmount\":%.2f,\"deductible\":%.2f,\"coverage\":%.0f%%,\"payableAmount\":%.2f}",
                claimAmount, deductible, coveredPercent * 100, payable))
            .message("Payable amount calculated: $" + String.format("%.2f", payable))
            .build();
    }

    private void simulateLatency(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
