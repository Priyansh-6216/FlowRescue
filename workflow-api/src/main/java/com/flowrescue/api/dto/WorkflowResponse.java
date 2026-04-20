package com.flowrescue.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowResponse {
    private String workflowId;
    private String status;
    private String currentStep;
    private List<String> completedSteps;
    private int retryCount;
    private String nextRetryAt;
    private String startedAt;
    private String updatedAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String failedStep;
    private String compensationReason;
    private String message;     // for idempotent replay messages
}
