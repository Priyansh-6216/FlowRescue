package com.flowrescue.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class StartWorkflowRequest {

    @NotBlank(message = "claimId is required")
    private String claimId;

    @NotBlank(message = "memberId is required")
    private String memberId;

    @NotBlank(message = "providerId is required")
    private String providerId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private Double amount;

    @NotBlank(message = "diagnosisCode is required")
    private String diagnosisCode;

    @NotBlank(message = "serviceDate is required")
    private String serviceDate;
}
