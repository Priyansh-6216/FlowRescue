package com.flowrescue.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowrescue.api.dto.StartWorkflowRequest;
import com.flowrescue.api.repository.IdempotencyRepository;
import com.flowrescue.common.model.IdempotencyRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Returns Optional.empty() if this is a brand-new request (caller should proceed).
     * Returns Optional.of(record) if key already exists (caller should replay).
     * Throws IllegalArgumentException if the same key was used with a different payload.
     */
    public Optional<IdempotencyRecord> checkAndReserve(String idempotencyKey,
                                                        StartWorkflowRequest request) {
        String requestHash = hashRequest(request);
        Optional<IdempotencyRecord> existing = idempotencyRepository.findByKey(idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IllegalArgumentException(
                    "Idempotency key '" + idempotencyKey + "' was already used with a different request payload."
                );
            }
            log.info("Idempotency hit for key={} workflowId={}", idempotencyKey, record.getWorkflowId());
            return existing;
        }

        // Reserve the key before starting processing
        IdempotencyRecord newRecord = IdempotencyRecord.builder()
            .idempotencyKey(idempotencyKey)
            .requestHash(requestHash)
            .status("IN_PROGRESS")
            .createdAt(Instant.now().toString())
            .expiryEpoch(Instant.now().plusSeconds(86400 * 7).getEpochSecond()) // 7 day TTL
            .build();

        idempotencyRepository.save(newRecord);
        return Optional.empty();
    }

    public void markCompleted(String idempotencyKey, String workflowId, String responseSnapshot) {
        idempotencyRepository.updateStatus(idempotencyKey, workflowId, "COMPLETED", responseSnapshot);
    }

    private String hashRequest(StartWorkflowRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash request", e);
        }
    }
}
