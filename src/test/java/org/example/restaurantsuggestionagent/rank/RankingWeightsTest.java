package org.example.restaurantsuggestionagent.rank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.example.restaurantsuggestionagent.rank.RankTestData.DEFAULTS;
import static org.example.restaurantsuggestionagent.rank.RankTestData.query;

import java.util.List;

import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.RankingFactor;
import org.junit.jupiter.api.Test;

class RankingWeightsTest {

    @Test
    void withoutBudgetPriceIsDroppedAndTheRestScaledToOne() {
        RankingWeights w = RankingWeights.forRequest(DEFAULTS, 2, query(DietPreference.VEGETARIAN, 0, List.of(), List.of()));

        assertThat(w.of(RankingFactor.PRICE_MATCH)).isZero();
        assertThat(w.weights().values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1, within(1e-9));
        assertThat(w.of(RankingFactor.FOOD_MATCH)).isCloseTo(20 / 80.0, within(1e-9));
        assertThat(w.notes()).containsExactly("PRICE_MATCH not used (no budget given)");
    }

    @Test
    void mainlyCaringAboutVegetarianOptionsDoublesDietaryMatch() {
        RankingWeights w = RankingWeights.forRequest(DEFAULTS, 2,
                query(DietPreference.VEGETARIAN, 0, List.of(), List.of(RankingFactor.DIETARY_MATCH)));

        // No budget, so price is dropped; dietary doubles to 40 out of 20+40+10+10+20 = 100.
        assertThat(w.of(RankingFactor.DIETARY_MATCH)).isCloseTo(0.40, within(1e-9));
        assertThat(w.describe()).startsWith("DIETARY_MATCH 40%, FOOD_MATCH 20%");
        assertThat(w.notes()).contains("DIETARY_MATCH counts 2× (you said it matters most)");
    }

    @Test
    void onlyFifteenEurosMakesPriceTheBiggestWeight() {
        RankingWeights w = RankingWeights.forRequest(DEFAULTS, 2,
                query(DietPreference.VEGETARIAN, 15, List.of(), List.of(RankingFactor.PRICE_MATCH)));

        assertThat(w.describe()).startsWith("PRICE_MATCH 27%"); // 30 out of 20+20+30+10+10+20 = 110
        assertThat(w.of(RankingFactor.PRICE_MATCH)).isGreaterThan(w.of(RankingFactor.FOOD_MATCH));
    }

    @Test
    void anyDietDropsDietaryMatch() {
        RankingWeights w = RankingWeights.forRequest(DEFAULTS, 2, query(DietPreference.ANY, 20, List.of(), List.of()));

        assertThat(w.of(RankingFactor.DIETARY_MATCH)).isZero();
        assertThat(w.of(RankingFactor.PRICE_MATCH)).isGreaterThan(0);
    }
}
