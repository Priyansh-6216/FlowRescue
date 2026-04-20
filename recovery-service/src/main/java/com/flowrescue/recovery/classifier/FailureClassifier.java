package com.flowrescue.recovery.classifier;

import com.flowrescue.common.dto.FailureEvent;
import com.flowrescue.common.enums.FailureDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Deterministic failure classification engine.
 *
 * Rules (in priority order):
 * 1. Non-retryable error codes → MANUAL_REVIEW or FAIL_FINAL
 * 2. Retryable + retryCount < maxImmediate → RETRY_NOW
 * 3. Retryable + retryCount between maxImmediate and maxDelayed → RETRY_LATER
 * 4. Retryable + retryCount >= maxDelayed → evaluate if compensation needed
 * 5. Step has side effects with partial completion → COMPENSATE
 * 6. Otherwise → MANUAL_REVIEW
 */
@Component
@Slf4j
public class FailureClassifier {

    private static final Set<String> TERMINAL_ERROR_CODES = Set.of(
        "INVALID_MEMBER_ID",
        "DUPLICATE_CLAIM_DETECTED",
        "POLICY_INELIGIBLE",
        "MALFORMED_PAYLOAD"
    );

    private static final Set<String> RETRYABLE_ERROR_CODES = Set.of(
        "MEMBER_VALIDATION_FAILED",
        "DUPLICATE_CHECK_UNAVAILABLE",
        "PRICING_ENGINE_ERROR",
        "PAYMENT_SERVICE_TIMEOUT",
        "DB_WRITE_FAILED",
        "NOTIFICATION_DELIVERY_FAILED",
        "UNEXPECTED_ERROR"
    );

    private static final Set<String> SIDE_EFFECT_STEPS = Set.of(
        "CREATE_PAYMENT_INSTRUCTION",
        "PERSIST_CLAIM_DECISION",
        "SEND_NOTIFICATION"
    );

    @Value("${recovery.retry.max-immediate:3}")
    private int maxImmediate;

    @Value("${recovery.retry.max-delayed:5}")
    private int maxDelayed;

    public FailureDecision classify(FailureEvent event) {
        String errorCode = event.getErrorCode();
        String failedStep = event.getFailedStep();
        int retryCount = event.getRetryCount();
        boolean exceptionIsRetryable = "RetryableException".equals(event.getExceptionType());

        log.info("[{}] Classifying failure: step={} errorCode={} retryCount={} retryable={}",
            event.getWorkflowId(), failedStep, errorCode, retryCount, exceptionIsRetryable);

        // Rule 1: Terminal business errors
        if (TERMINAL_ERROR_CODES.contains(errorCode)) {
            log.info("[{}] → FAIL_FINAL (terminal error code: {})", event.getWorkflowId(), errorCode);
            return FailureDecision.FAIL_FINAL;
        }

        // Rule 2: Retryable + below immediate threshold
        if (exceptionIsRetryable && retryCount < maxImmediate) {
            log.info("[{}] → RETRY_NOW (retryCount={} < maxImmediate={})",
                event.getWorkflowId(), retryCount, maxImmediate);
            return FailureDecision.RETRY_NOW;
        }

        // Rule 3: Retryable + above immediate but below delayed threshold
        if (exceptionIsRetryable && retryCount < maxDelayed) {
            log.info("[{}] → RETRY_LATER (retryCount={} scheduled for delayed retry)",
                event.getWorkflowId(), retryCount);
            return FailureDecision.RETRY_LATER;
        }

        // Rule 4: Max retries exhausted — check if compensation is needed
        if (hasSideEffectCompleted(event)) {
            log.info("[{}] → COMPENSATE (side-effect steps completed, max retries exhausted)",
                event.getWorkflowId());
            return FailureDecision.COMPENSATE;
        }

        // Rule 5: No side effects to undo, escalate
        log.info("[{}] → MANUAL_REVIEW (max retries exhausted, no compensation needed)",
            event.getWorkflowId());
        return FailureDecision.MANUAL_REVIEW;
    }

    private boolean hasSideEffectCompleted(FailureEvent event) {
        if (event.getCompletedSteps() == null) return false;
        return event.getCompletedSteps().stream()
            .anyMatch(SIDE_EFFECT_STEPS::contains);
    }
}
