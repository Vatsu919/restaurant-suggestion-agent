package org.example.restaurantsuggestionagent.agent;

import org.example.restaurantsuggestionagent.events.StepType;

/**
 * A restaurant dropped from the run, kept so the final response can list what was
 * skipped and why.
 *
 * @param detail the reason in words, e.g. "No website listed on Google"
 */
public record SkippedRestaurant(
        String id,
        String name,
        String websiteUri,
        String googleMapsUri,
        StepType step,
        SkipReason reason,
        String detail) {
}
