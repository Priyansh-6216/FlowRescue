package com.flowrescue.taskrunner.step;

import com.flowrescue.common.model.WorkflowExecution;
import com.flowrescue.taskrunner.config.FailureInjectionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimPersistenceStep implements StepHandler {

    private final FailureInjectionConfig failureConfig;

    @Override
    public String stepName() { return "PERSIST_CLAIM_DECISION"; }

    @Override
    public StepResult execute(WorkflowExecution execution) throws StepException {
        log.info("[{}] Executing PERSIST_CLAIM_DECISION for claimId={}", execution.getWorkflowId(), execution.getClaimId());

        if (failureConfig.shouldFail(stepName())) {
            throw new StepException("DB_WRITE_FAILED",
                "Database write failed — connection pool exhausted (retryable)", true);
        }

        simulateLatency(100);

        // THIS STEP HAS SIDE EFFECTS — persists the adjudication decision
        log.info("[{}] PERSIST_CLAIM_DECISION ✓ — claim {} adjudicated as APPROVED", execution.getWorkflowId(), execution.getClaimId());

        return StepResult.builder()
            .stepName(stepName())
            .success(true)
            .resultPayload(String.format(
                "{\"claimId\":\"%s\",\"decision\":\"APPROVED\",\"adjudicationId\":\"ADJ-%s\",\"persistedAt\":\"%s\"}",
                execution.getClaimId(),
                execution.getWorkflowId().substring(3, 11).toUpperCase(),
                java.time.Instant.now()))
            .message("Claim decision persisted — APPROVED")
            .build();
    }

    private void simulateLatency(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
