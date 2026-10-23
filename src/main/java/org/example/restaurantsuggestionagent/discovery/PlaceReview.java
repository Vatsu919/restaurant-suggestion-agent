package org.example.restaurantsuggestionagent.discovery;

/**
 * A review as returned by Google Places (at most 5 per place, chosen by Google).
 *
 * @param text             review translated into the requested language
 * @param originalText     review as the author wrote it
 * @param originalLanguage ISO code of {@code originalText}, e.g. "es"
 * @param publishTime      ISO-8601 timestamp
 */
public record PlaceReview(
        Double rating,
        String text,
        String originalText,
        String originalLanguage,
        String author,
        String publishTime,
        String relativePublishTime) {
}
