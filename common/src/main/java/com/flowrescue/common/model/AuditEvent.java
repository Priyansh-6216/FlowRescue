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
public class AuditEvent {

    private String workflowId;
    private String eventTime;        // sort key (ISO-8601 + nanos for uniqueness)
    private String eventType;
    private String stepName;
    private String fromStatus;
    private String toStatus;
    private String message;
    private String actor;
    private String metadata;

    @DynamoDbPartitionKey
    public String getWorkflowId() { return workflowId; }

    @DynamoDbSortKey
    public String getEventTime() { return eventTime; }

    @DynamoDbAttribute("eventType")
    public String getEventType() { return eventType; }

    @DynamoDbAttribute("stepName")
    public String getStepName() { return stepName; }

    @DynamoDbAttribute("fromStatus")
    public String getFromStatus() { return fromStatus; }

    @DynamoDbAttribute("toStatus")
    public String getToStatus() { return toStatus; }

    @DynamoDbAttribute("message")
    public String getMessage() { return message; }

    @DynamoDbAttribute("actor")
    public String getActor() { return actor; }

    @DynamoDbAttribute("metadata")
    public String getMetadata() { return metadata; }
}
