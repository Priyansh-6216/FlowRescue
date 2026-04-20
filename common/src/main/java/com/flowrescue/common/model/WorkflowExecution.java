package com.flowrescue.common.model;

import com.flowrescue.common.enums.WorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class WorkflowExecution {

    private String workflowId;
    private String workflowType;
    private String status;
    private String currentStep;

    @Builder.Default
    private List<String> completedSteps = new ArrayList<>();

    private String claimId;
    private String memberId;
    private String providerId;
    private Double amount;
    private String diagnosisCode;
    private String serviceDate;

    private String lastErrorCode;
    private String lastErrorMessage;
    private String failedStep;

    private int retryCount;
    private String nextRetryAt;
    private Long version;

    private String startedAt;
    private String updatedAt;
    private Long ttl;

    // Compensation tracking
    private String compensationReason;
    private String compensatedAt;

    @DynamoDbPartitionKey
    public String getWorkflowId() { return workflowId; }

    @DynamoDbAttribute("workflowType")
    public String getWorkflowType() { return workflowType; }

    @DynamoDbAttribute("status")
    public String getStatus() { return status; }

    @DynamoDbAttribute("currentStep")
    public String getCurrentStep() { return currentStep; }

    @DynamoDbAttribute("completedSteps")
    public List<String> getCompletedSteps() { return completedSteps; }

    @DynamoDbAttribute("claimId")
    public String getClaimId() { return claimId; }

    @DynamoDbAttribute("memberId")
    public String getMemberId() { return memberId; }

    @DynamoDbAttribute("providerId")
    public String getProviderId() { return providerId; }

    @DynamoDbAttribute("amount")
    public Double getAmount() { return amount; }

    @DynamoDbAttribute("diagnosisCode")
    public String getDiagnosisCode() { return diagnosisCode; }

    @DynamoDbAttribute("serviceDate")
    public String getServiceDate() { return serviceDate; }

    @DynamoDbAttribute("lastErrorCode")
    public String getLastErrorCode() { return lastErrorCode; }

    @DynamoDbAttribute("lastErrorMessage")
    public String getLastErrorMessage() { return lastErrorMessage; }

    @DynamoDbAttribute("failedStep")
    public String getFailedStep() { return failedStep; }

    @DynamoDbAttribute("retryCount")
    public int getRetryCount() { return retryCount; }

    @DynamoDbAttribute("nextRetryAt")
    public String getNextRetryAt() { return nextRetryAt; }

    @DynamoDbAttribute("version")
    public Long getVersion() { return version; }

    @DynamoDbAttribute("startedAt")
    public String getStartedAt() { return startedAt; }

    @DynamoDbAttribute("updatedAt")
    public String getUpdatedAt() { return updatedAt; }

    @DynamoDbAttribute("ttl")
    public Long getTtl() { return ttl; }

    @DynamoDbAttribute("compensationReason")
    public String getCompensationReason() { return compensationReason; }

    @DynamoDbAttribute("compensatedAt")
    public String getCompensatedAt() { return compensatedAt; }
}
