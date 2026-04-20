package com.flowrescue.api.controller;

import com.flowrescue.api.dto.StartWorkflowRequest;
import com.flowrescue.api.dto.WorkflowResponse;
import com.flowrescue.api.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class WorkflowController {

    private final WorkflowService workflowService;

    /**
     * POST /api/v1/workflows/claims
     * Start a new healthcare claim workflow.
     * Requires Idempotency-Key header.
     */
    @PostMapping("/claims")
    public ResponseEntity<WorkflowResponse> startClaimWorkflow(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StartWorkflowRequest request) {

        log.info("POST /claims — claimId={} idempotencyKey={}", request.getClaimId(), idempotencyKey);
        WorkflowResponse response = workflowService.startWorkflow(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * GET /api/v1/workflows/{workflowId}
     * Get current status of a workflow.
     */
    @GetMapping("/{workflowId}")
    public ResponseEntity<WorkflowResponse> getWorkflow(@PathVariable String workflowId) {
        return ResponseEntity.ok(workflowService.getStatus(workflowId));
    }

    /**
     * GET /api/v1/workflows
     * List all workflows (for dashboard).
     */
    @GetMapping
    public ResponseEntity<List<WorkflowResponse>> listWorkflows() {
        return ResponseEntity.ok(workflowService.listAll());
    }

    /**
     * POST /api/v1/workflows/{workflowId}/recover
     * Force a recovery attempt for a failed workflow.
     */
    @PostMapping("/{workflowId}/recover")
    public ResponseEntity<WorkflowResponse> forceRecover(@PathVariable String workflowId) {
        log.info("POST /{}/recover — manual force recovery", workflowId);
        return ResponseEntity.ok(workflowService.forceRecover(workflowId));
    }

    /**
     * POST /api/v1/workflows/{workflowId}/compensate
     * Manually trigger compensation for all reversible completed steps.
     */
    @PostMapping("/{workflowId}/compensate")
    public ResponseEntity<WorkflowResponse> manualCompensate(@PathVariable String workflowId) {
        log.info("POST /{}/compensate — manual compensation", workflowId);
        return ResponseEntity.ok(workflowService.manualCompensate(workflowId));
    }

    /**
     * POST /api/v1/workflows/{workflowId}/replay
     * Replay from a specific step (admin / testing).
     */
    @PostMapping("/{workflowId}/replay")
    public ResponseEntity<WorkflowResponse> replayFromStep(
            @PathVariable String workflowId,
            @RequestBody Map<String, String> body) {

        String fromStep = body.get("fromStep");
        log.info("POST /{}/replay — fromStep={}", workflowId, fromStep);
        return ResponseEntity.ok(workflowService.replayFromStep(workflowId, fromStep));
    }

    /**
     * GET /api/v1/workflows/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "workflow-api"));
    }
}
