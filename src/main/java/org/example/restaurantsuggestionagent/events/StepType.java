package org.example.restaurantsuggestionagent.events;

/**
 * The fixed stages of the pipeline, in execution order. RUN is used for events
 * about the run as a whole (started, finished, failed).
 */
public enum StepType {
    RUN,
    PARSE_QUERY,
    DISCOVER,
    FILTER,
    RESOLVE_MENU,
    EXTRACT_MENU,
    ANALYZE_REVIEWS,
    RANK,
    SUMMARIZE
}
