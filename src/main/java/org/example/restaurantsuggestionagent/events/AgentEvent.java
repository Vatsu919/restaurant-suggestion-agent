package org.example.restaurantsuggestionagent.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One entry in a run's timeline, streamed to the UI as it happens.
 *
 * @param seq          1-based position within the run; doubles as the SSE event id
 * @param restaurantId Google place id when the event concerns one restaurant, otherwise null
 * @param data         structured details for the UI (counts, URLs, items); never null
 */
public record AgentEvent(
        UUID runId,
        int seq,
        StepType step,
        EventStatus status,
        String restaurantId,
        String message,
        Map<String, Object> data,
        Instant at) {
}
