package com.flowrescue.compensation.handler;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompensationResult {
    private String stepName;
    private boolean success;
    private String message;
}
