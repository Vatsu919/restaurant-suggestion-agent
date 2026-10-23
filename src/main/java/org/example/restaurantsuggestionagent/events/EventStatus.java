package org.example.restaurantsuggestionagent.events;

public enum EventStatus {
    STARTED,
    PROGRESS,
    COMPLETED,
    /** A restaurant was dropped from the run; the event message says why. */
    SKIPPED,
    FAILED
}
