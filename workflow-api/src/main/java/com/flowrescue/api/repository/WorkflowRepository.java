package com.flowrescue.api.repository;

import com.flowrescue.common.model.WorkflowExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class WorkflowRepository {

    private final DynamoDbEnhancedClient enhancedClient;

    private DynamoDbTable<WorkflowExecution> table() {
        return enhancedClient.table("WorkflowExecution",
            TableSchema.fromBean(WorkflowExecution.class));
    }

    public void save(WorkflowExecution execution) {
        table().putItem(execution);
        log.debug("Saved workflow {} with status {}", execution.getWorkflowId(), execution.getStatus());
    }

    public Optional<WorkflowExecution> findById(String workflowId) {
        WorkflowExecution result = table().getItem(
            Key.builder().partitionValue(workflowId).build()
        );
        return Optional.ofNullable(result);
    }

    public List<WorkflowExecution> findAll() {
        return table().scan().items().stream().collect(Collectors.toList());
    }

    public void update(WorkflowExecution execution) {
        table().updateItem(execution);
        log.debug("Updated workflow {} → status={}", execution.getWorkflowId(), execution.getStatus());
    }
}
