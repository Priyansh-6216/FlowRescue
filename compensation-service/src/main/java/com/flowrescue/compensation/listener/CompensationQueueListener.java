package com.flowrescue.compensation.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowrescue.common.dto.CompensationEvent;
import com.flowrescue.compensation.service.CompensationService;
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
public class CompensationQueueListener {

    private final SqsClient sqsClient;
    private final CompensationService compensationService;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queues.compensation}")
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
            log.error("Error polling compensation-queue: {}", e.getMessage());
        }
    }

    private void processMessage(Message message) {
        try {
            CompensationEvent event = objectMapper.readValue(
                message.body(), CompensationEvent.class);

            log.info("Compensation event received: workflowId={} steps={}",
                event.getWorkflowId(), event.getStepsToCompensate());

            compensationService.compensate(event);

            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());

        } catch (Exception e) {
            log.error("Failed to process compensation message: {}", e.getMessage());
        }
    }
}
