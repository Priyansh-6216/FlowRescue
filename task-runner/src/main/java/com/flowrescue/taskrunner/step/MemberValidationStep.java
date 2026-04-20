package com.flowrescue.taskrunner.step;

import com.flowrescue.common.model.WorkflowExecution;
import com.flowrescue.taskrunner.config.FailureInjectionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberValidationStep implements StepHandler {

    private final FailureInjectionConfig failureConfig;

    @Override
    public String stepName() { return "VALIDATE_MEMBER"; }

    @Override
    public StepResult execute(WorkflowExecution execution) throws StepException {
        log.info("[{}] Executing VALIDATE_MEMBER for member={}", execution.getWorkflowId(), execution.getMemberId());

        if (failureConfig.shouldFail(stepName())) {
            throw new StepException("MEMBER_VALIDATION_FAILED",
                "Member validation service timeout — simulated failure", true);
        }

        // Simulate validation logic: memberId must start with "MBR-"
        if (execution.getMemberId() == null || !execution.getMemberId().startsWith("MBR-")) {
            throw new StepException("INVALID_MEMBER_ID",
                "Member ID format is invalid: " + execution.getMemberId(), false);
        }

        simulateLatency(80);
        log.info("[{}] VALIDATE_MEMBER ✓ — member {} is active", execution.getWorkflowId(), execution.getMemberId());

        return StepResult.builder()
            .stepName(stepName())
            .success(true)
            .resultPayload("{\"memberId\":\"" + execution.getMemberId() + "\",\"status\":\"ACTIVE\",\"plan\":\"PREMIUM_PPO\"}")
            .message("Member validated successfully")
            .build();
    }

    private void simulateLatency(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
