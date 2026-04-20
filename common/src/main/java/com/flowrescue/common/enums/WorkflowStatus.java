package com.flowrescue.common.enums;

public enum WorkflowStatus {
    CREATED,
    RUNNING,
    FAILED,
    RETRY_SCHEDULED,
    COMPENSATING,
    COMPENSATED,
    MANUAL_REVIEW,
    SUCCESS
}
