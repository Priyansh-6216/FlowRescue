package com.flowrescue.taskrunner.step;

import com.flowrescue.common.model.WorkflowExecution;
import com.flowrescue.taskrunner.config.FailureInjectionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DuplicateCheckStep implements StepHandler {

    private final FailureInjectionConfig failureConfig;

    @Override
    public String stepName() { return "CHECK_DUPLICATE_CLAIM"; }

    @Override
    public StepResult execute(WorkflowExecution execution) throws StepException {
        log.info("[{}] Executing CHECK_DUPLICATE_CLAIM for claimId={}", execution.getWorkflowId(), execution.getClaimId());

        if (failureConfig.shouldFail(stepName())) {
            throw new StepException("DUPLICATE_CHECK_UNAVAILABLE",
                "Duplicate check service is temporarily unavailable", true);
        }

        simulateLatency(120);

        // Simulate: claims starting with "CLM-DUP" are duplicates
        if (execution.getClaimId() != null && execution.getClaimId().startsWith("CLM-DUP")) {
            throw new StepException("DUPLICATE_CLAIM_DETECTED",
                "Claim " + execution.getClaimId() + " is a duplicate of an existing claim", false);
        }

        log.info("[{}] CHECK_DUPLICATE_CLAIM ✓ — no duplicate found", execution.getWorkflowId());

        return StepResult.builder()
            .stepName(stepName())
            .success(true)
            .resultPayload("{\"claimId\":\"" + execution.getClaimId() + "\",\"isDuplicate\":false,\"checkTimestamp\":\"" + java.time.Instant.now() + "\"}")
            .message("No duplicate claim found")
            .build();
    }

    private void simulateLatency(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
