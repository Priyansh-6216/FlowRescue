package com.flowrescue.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Message published to the workflow-task-queue SQS queue.
 * The task-runner picks this up and begins (or resumes) execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTaskEvent {
    private String workflowId;
    private String workflowType;
    private String fromStep;          // null = start from beginning; non-null = resume from step
    private String claimId;
    private String memberId;
    private String providerId;
    private Double amount;
    private String diagnosisCode;
    private String serviceDate;
    private int retryAttempt;
}
