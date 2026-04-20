package com.flowrescue.taskrunner.step;

/**
 * Base exception for all step failures.
 */
public class StepException extends Exception {
    private final String errorCode;
    private final boolean retryable;

    public StepException(String errorCode, String message, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public StepException(String errorCode, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String getErrorCode() { return errorCode; }
    public boolean isRetryable() { return retryable; }
}
