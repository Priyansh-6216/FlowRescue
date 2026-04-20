package com.flowrescue.taskrunner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "failure.injection")
@Data
public class FailureInjectionConfig {

    private boolean validateMember = false;
    private boolean duplicateCheck = false;
    private boolean pricing = false;
    private boolean paymentInstruction = false;
    private boolean claimPersistence = false;
    private boolean notification = false;

    // Toggle at runtime via this helper
    public boolean shouldFail(String stepName) {
        return switch (stepName) {
            case "VALIDATE_MEMBER" -> validateMember;
            case "CHECK_DUPLICATE_CLAIM" -> duplicateCheck;
            case "CALCULATE_PAYABLE" -> pricing;
            case "CREATE_PAYMENT_INSTRUCTION" -> paymentInstruction;
            case "PERSIST_CLAIM_DECISION" -> claimPersistence;
            case "SEND_NOTIFICATION" -> notification;
            default -> false;
        };
    }

    public void setFlag(String stepName, boolean value) {
        switch (stepName) {
            case "VALIDATE_MEMBER" -> validateMember = value;
            case "CHECK_DUPLICATE_CLAIM" -> duplicateCheck = value;
            case "CALCULATE_PAYABLE" -> pricing = value;
            case "CREATE_PAYMENT_INSTRUCTION" -> paymentInstruction = value;
            case "PERSIST_CLAIM_DECISION" -> claimPersistence = value;
            case "SEND_NOTIFICATION" -> notification = value;
        }
    }
}
