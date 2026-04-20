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
public class RecoverySchedule {

    private String workflowId;
    private String scheduleId;
    private String scheduledFor;   // ISO-8601
    private String reason;
    private String createdAt;
    private boolean processed;

    @DynamoDbPartitionKey
    public String getWorkflowId() { return workflowId; }

    @DynamoDbAttribute("scheduleId")
    public String getScheduleId() { return scheduleId; }

    @DynamoDbAttribute("scheduledFor")
    public String getScheduledFor() { return scheduledFor; }

    @DynamoDbAttribute("reason")
    public String getReason() { return reason; }

    @DynamoDbAttribute("createdAt")
    public String getCreatedAt() { return createdAt; }

    @DynamoDbAttribute("processed")
    public boolean isProcessed() { return processed; }
}
