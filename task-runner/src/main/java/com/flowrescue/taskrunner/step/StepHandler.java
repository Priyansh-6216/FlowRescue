package com.flowrescue.taskrunner.step;

import com.flowrescue.common.model.WorkflowExecution;

/**
 * Contract for every business step handler.
 * Each step receives the current workflow state, performs its logic,
 * and returns a result payload (stored in audit / passed forward).
 */
public interface StepHandler {

    /**
     * The step name this handler handles (matches StepName enum).
     */
    String stepName();

    /**
     * Execute the step.
     * Throws RetryableStepException for transient failures.
     * Throws BusinessStepException for terminal / non-retryable failures.
     */
    StepResult execute(WorkflowExecution execution) throws StepException;
}
