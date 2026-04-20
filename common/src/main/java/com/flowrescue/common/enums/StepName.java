package com.flowrescue.common.enums;

public enum StepName {
    VALIDATE_MEMBER,
    CHECK_DUPLICATE_CLAIM,
    CALCULATE_PAYABLE,
    CREATE_PAYMENT_INSTRUCTION,
    PERSIST_CLAIM_DECISION,
    SEND_NOTIFICATION;

    public boolean hasSideEffect() {
        return this == CREATE_PAYMENT_INSTRUCTION
            || this == PERSIST_CLAIM_DECISION
            || this == SEND_NOTIFICATION;
    }
}
