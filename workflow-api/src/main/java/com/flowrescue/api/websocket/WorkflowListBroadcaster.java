package com.flowrescue.api.websocket;

import com.flowrescue.api.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowListBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final WorkflowService workflowService;

    /**
     * Push the complete workflow list to /topic/workflows/list every 3 seconds.
     * The dashboard subscribes to this to keep its table live.
     */
    @Scheduled(fixedDelay = 3000)
    public void broadcastList() {
        try {
            var workflows = workflowService.listAll();
            messagingTemplate.convertAndSend("/topic/workflows/list", workflows);
        } catch (Exception e) {
            log.debug("Broadcast list skipped: {}", e.getMessage());
        }
    }
}
