package com.flowrescue.compensation.handler;

/**
 * Contract for compensation handlers.
 * Each handler reverses the side effects of one forward step.
 */
public interface CompensationHandler {

    /** The step name this handler compensates. */
    String stepName();

    /** Execute the compensation. Idempotent — safe to call multiple times. */
    CompensationResult compensate(String workflowId);
}
