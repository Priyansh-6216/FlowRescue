package com.flowrescue.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Published to the recovery-queue when a workflow step fails.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureEvent {
    private String workflowId;
    private String failedStep;
    private String errorCode;
    private String errorMessage;
    private String exceptionType;       // e.g. "RetryableException", "BusinessException"
    private int retryCount;
    private List<String> completedSteps;
    private String occurredAt;
}
