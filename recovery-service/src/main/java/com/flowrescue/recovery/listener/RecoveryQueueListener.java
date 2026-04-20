package com.flowrescue.recovery.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowrescue.common.dto.FailureEvent;
import com.flowrescue.recovery.service.RecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecoveryQueueListener {

    private final SqsClient sqsClient;
    private final RecoveryService recoveryService;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queues.recovery}")
    private String queueUrl;

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        try {
            ReceiveMessageResponse response = sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(5)
                    .waitTimeSeconds(5)
                    .build()
            );

            List<Message> messages = response.messages();
            for (Message message : messages) {
                processMessage(message);
            }
        } catch (Exception e) {
            log.error("Error polling recovery-queue: {}", e.getMessage());
        }
    }

    private void processMessage(Message message) {
        try {
            FailureEvent event = objectMapper.readValue(message.body(), FailureEvent.class);
            log.info("Recovery message received: workflowId={} failedStep={} retryCount={}",
                event.getWorkflowId(), event.getFailedStep(), event.getRetryCount());

            recoveryService.handle(event);

            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
        } catch (Exception e) {
            log.error("Failed to process recovery message: {}", e.getMessage());
        }
    }
}
