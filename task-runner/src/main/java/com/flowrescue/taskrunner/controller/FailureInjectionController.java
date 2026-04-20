package com.flowrescue.taskrunner.controller;

import com.flowrescue.taskrunner.config.FailureInjectionConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin API to toggle failure injection flags at runtime.
 * Used by the dashboard's "Inject Failure" panel.
 */
@RestController
@RequestMapping("/api/v1/inject")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FailureInjectionController {

    private final FailureInjectionConfig config;

    @GetMapping
    public ResponseEntity<Map<String, Boolean>> getFlags() {
        return ResponseEntity.ok(Map.of(
            "VALIDATE_MEMBER",            config.isValidateMember(),
            "CHECK_DUPLICATE_CLAIM",      config.isDuplicateCheck(),
            "CALCULATE_PAYABLE",          config.isPricing(),
            "CREATE_PAYMENT_INSTRUCTION", config.isPaymentInstruction(),
            "PERSIST_CLAIM_DECISION",     config.isClaimPersistence(),
            "SEND_NOTIFICATION",          config.isNotification()
        ));
    }

    @PostMapping("/{stepName}")
    public ResponseEntity<Map<String, Object>> setFlag(
            @PathVariable String stepName,
            @RequestBody Map<String, Boolean> body) {

        Boolean fail = body.get("fail");
        if (fail == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Request body must contain 'fail' boolean"));
        }
        config.setFlag(stepName, fail);
        return ResponseEntity.ok(Map.of(
            "step", stepName,
            "failureEnabled", fail,
            "message", fail
                ? "⚠️ Failure injection ENABLED for " + stepName
                : "✅ Failure injection DISABLED for " + stepName
        ));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> resetAll() {
        config.setValidateMember(false);
        config.setDuplicateCheck(false);
        config.setPricing(false);
        config.setPaymentInstruction(false);
        config.setClaimPersistence(false);
        config.setNotification(false);
        return ResponseEntity.ok(Map.of("message", "All failure injection flags cleared"));
    }
}
