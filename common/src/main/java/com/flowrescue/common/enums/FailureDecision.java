package com.flowrescue.common.enums;

public enum FailureDecision {
    RETRY_NOW,
    RETRY_LATER,
    COMPENSATE,
    MANUAL_REVIEW,
    FAIL_FINAL
}
