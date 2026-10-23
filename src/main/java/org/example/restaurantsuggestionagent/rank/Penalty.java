package org.example.restaurantsuggestionagent.rank;

/** Points taken off a score, and why, e.g. 5 for "a ★2 review: \"Very slow service\"". */
public record Penalty(double points, String reason) {
}
