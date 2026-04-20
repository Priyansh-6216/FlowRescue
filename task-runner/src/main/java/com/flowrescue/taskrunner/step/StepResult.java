package com.flowrescue.taskrunner.step;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StepResult {
    private String stepName;
    private boolean success;
    private String resultPayload;   // JSON string of step output
    private String message;
}
