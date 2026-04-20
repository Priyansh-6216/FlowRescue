package com.flowrescue.taskrunner.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowrescue.common.dto.WorkflowTaskEvent;
import com.flowrescue.taskrunner.orchestrator.WorkflowOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;

/**
 * Polls the workflow-task-queue SQS queue and dispatches
 * each message to the WorkflowOrchestrator for execution.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskQueueListener {

    private final SqsClient sqsClient;
    private final WorkflowOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queues.workflow-task}")
    private String queueUrl;

    @Value("${sqs.poll.wait-seconds:5}")
    private int waitSeconds;

    @Value("${sqs.poll.max-messages:5}")
    private int maxMessages;

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        try {
            ReceiveMessageResponse response = sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(maxMessages)
                    .waitTimeSeconds(waitSeconds)
                    .build()
            );

            List<Message> messages = response.messages();
            if (!messages.isEmpty()) {
                log.debug("Received {} task message(s) from SQS", messages.size());
            }

            for (Message message : messages) {
                processMessage(message);
            }

        } catch (Exception e) {
            log.error("Error polling workflow-task-queue: {}", e.getMessage());
        }
    }

    private void processMessage(Message message) {
        String receiptHandle = message.receiptHandle();
        try {
            WorkflowTaskEvent event = objectMapper.readValue(
                message.body(), WorkflowTaskEvent.class);

            log.info("Processing workflow task: workflowId={} fromStep={}",
                event.getWorkflowId(), event.getFromStep());

            orchestrator.execute(event);

            // Delete message only after successful processing
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build());

        } catch (Exception e) {
            log.error("Failed to process task message: {} — leaving in queue for retry",
                e.getMessage());
            // Message visibility timeout will expire and it will be redelivered
        }
    }
}
