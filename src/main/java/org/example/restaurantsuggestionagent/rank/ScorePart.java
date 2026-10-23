package org.example.restaurantsuggestionagent.rank;

import org.example.restaurantsuggestionagent.query.RankingFactor;

/**
 * One dimension of a score.
 *
 * @param score  how good the restaurant is on this dimension, from 0 to 100
 * @param weight this dimension's share of the total, from 0 to 1
 * @param points what it adds to the total: score × weight
 * @param reason the dimension in words, e.g. "14 of 20 dishes suit vegetarian"
 */
public record ScorePart(RankingFactor factor, double score, double weight, double points, String reason) {
}
