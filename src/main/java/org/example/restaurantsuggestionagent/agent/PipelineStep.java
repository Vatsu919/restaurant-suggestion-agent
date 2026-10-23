package org.example.restaurantsuggestionagent.agent;

import org.example.restaurantsuggestionagent.events.StepType;

/**
 * One stage of the pipeline. Implementations are Spring beans annotated with
 * {@link org.springframework.core.annotation.Order}; the orchestrator runs them in that order.
 *
 * <p>A step reports its work through {@link RunContext#progress} and
 * {@link RunContext#skipped}. Throwing fails the whole run, so a problem with a
 * single restaurant should be reported as a skip instead.
 */
public interface PipelineStep {

    StepType type();

    /** Shown to the user when the step starts, e.g. "Searching Google Places for restaurants". */
    String description();

    void execute(RunContext context) throws Exception;
}
