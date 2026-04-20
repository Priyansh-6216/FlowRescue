package com.flowrescue.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Published to the compensation-queue to trigger reverse-order compensation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompensationEvent {
    private String workflowId;
    private List<String> stepsToCompensate;   // In reverse order of completion
    private String reason;
    private String triggeredAt;
}
