package com.flowrescue.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowrescue.api.dto.WorkflowResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.flowrescue.api.service.WorkflowService;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Push a single workflow update to all subscribers of /topic/workflows/{id}
     * and the global /topic/workflows feed.
     */
    public void broadcastWorkflowUpdate(WorkflowResponse response) {
        try {
            messagingTemplate.convertAndSend(
                "/topic/workflows/" + response.getWorkflowId(), response);
            messagingTemplate.convertAndSend("/topic/workflows", response);
            log.debug("Broadcast update for workflow {}", response.getWorkflowId());
        } catch (Exception e) {
            log.warn("Failed to broadcast workflow update: {}", e.getMessage());
        }
    }

    /**
     * Broadcasts the full workflow list every 3 seconds so the dashboard
     * stays in sync even when updates come from other services.
     */
    @Scheduled(fixedDelay = 3000)
    public void broadcastAllWorkflows() {
        // This method is a no-op here — the WorkflowListBroadcaster handles this
        // since it has access to WorkflowService. Kept as a hook for extension.
    }
}
