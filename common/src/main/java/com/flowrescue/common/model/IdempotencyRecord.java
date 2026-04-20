package com.flowrescue.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class IdempotencyRecord {

    private String idempotencyKey;
    private String requestHash;
    private String workflowId;
    private String status;
    private String responseSnapshot;
    private String createdAt;
    private Long expiryEpoch;

    @DynamoDbPartitionKey
    public String getIdempotencyKey() { return idempotencyKey; }

    @DynamoDbAttribute("requestHash")
    public String getRequestHash() { return requestHash; }

    @DynamoDbAttribute("workflowId")
    public String getWorkflowId() { return workflowId; }

    @DynamoDbAttribute("status")
    public String getStatus() { return status; }

    @DynamoDbAttribute("responseSnapshot")
    public String getResponseSnapshot() { return responseSnapshot; }

    @DynamoDbAttribute("createdAt")
    public String getCreatedAt() { return createdAt; }

    @DynamoDbAttribute("expiryEpoch")
    public Long getExpiryEpoch() { return expiryEpoch; }
}
