package com.flowrescue.api.repository;

import com.flowrescue.common.model.IdempotencyRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class IdempotencyRepository {

    private final DynamoDbEnhancedClient enhancedClient;

    private DynamoDbTable<IdempotencyRecord> table() {
        return enhancedClient.table("IdempotencyRecord",
            TableSchema.fromBean(IdempotencyRecord.class));
    }

    /**
     * Attempts to insert a new idempotency record.
     * Returns the existing record if the key already exists.
     */
    public Optional<IdempotencyRecord> findByKey(String key) {
        IdempotencyRecord result = table().getItem(
            Key.builder().partitionValue(key).build()
        );
        return Optional.ofNullable(result);
    }

    public void save(IdempotencyRecord record) {
        table().putItem(record);
        log.debug("Saved idempotency key {}", record.getIdempotencyKey());
    }

    public void updateStatus(String key, String workflowId, String status, String responseSnapshot) {
        IdempotencyRecord existing = table().getItem(
            Key.builder().partitionValue(key).build()
        );
        if (existing != null) {
            existing.setWorkflowId(workflowId);
            existing.setStatus(status);
            existing.setResponseSnapshot(responseSnapshot);
            table().putItem(existing);
        }
    }
}
