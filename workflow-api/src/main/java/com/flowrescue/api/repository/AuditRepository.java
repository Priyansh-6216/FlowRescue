package com.flowrescue.api.repository;

import com.flowrescue.common.model.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AuditRepository {

    private final DynamoDbEnhancedClient enhancedClient;

    private DynamoDbTable<AuditEvent> table() {
        return enhancedClient.table("AuditTrail",
            TableSchema.fromBean(AuditEvent.class));
    }

    public void save(AuditEvent event) {
        table().putItem(event);
    }

    public List<AuditEvent> findByWorkflowId(String workflowId) {
        return table().query(QueryConditional.keyEqualTo(
            Key.builder().partitionValue(workflowId).build()
        )).items().stream().collect(Collectors.toList());
    }
}
