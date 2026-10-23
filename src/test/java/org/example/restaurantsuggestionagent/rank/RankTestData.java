package org.example.restaurantsuggestionagent.rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.discovery.PlaceReview;
import org.example.restaurantsuggestionagent.extraction.DietTag;
import org.example.restaurantsuggestionagent.extraction.EggContent;
import org.example.restaurantsuggestionagent.extraction.MenuItem;
import org.example.restaurantsuggestionagent.extraction.MenuKind;
import org.example.restaurantsuggestionagent.extraction.MenuReading;
import org.example.restaurantsuggestionagent.query.Budget;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.DietStrictness;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.RankingFactor;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.example.restaurantsuggestionagent.reviews.ContextReviewFinder;
import org.example.restaurantsuggestionagent.reviews.ReviewInsight;

final class RankTestData {

    static final Map<RankingFactor, Double> DEFAULTS = RankStep.weights(20, 20, 15, 10, 10, 20);

    private RankTestData() {
    }

    static TripQuery query(DietPreference diet, double maxSpend, List<String> cuisines, List<RankingFactor> priorities) {
        return new TripQuery("Barcelona", "", List.of(Meal.BREAKFAST, Meal.DINNER), diet,
                diet == DietPreference.VEGETARIAN ? DietStrictness.LACTO_OVO : DietStrictness.UNSPECIFIED,
                maxSpend > 0 ? Budget.CHEAP : Budget.UNSPECIFIED, cuisines, List.of(), "", "en", maxSpend, priorities);
    }

    static TripQuery vegetarian() {
        return query(DietPreference.VEGETARIAN, 0, List.of(), List.of());
    }

    static MenuItem dish(String name, DietTag diet, double price, boolean liked) {
        return new MenuItem(name, name, "Tasty", "Mains", List.of(), price > 0 ? price + " €" : "", price, diet,
                EggContent.NO_EGG, "", liked);
    }

    /** A menu with {@code suitable} vegetarian dishes and {@code other} meat dishes, all at {@code price}. */
    static MenuReading menu(String id, int suitable, int other, double price) {
        List<MenuItem> all = new ArrayList<>();
        List<MenuItem> matching = new ArrayList<>();
        for (int i = 0; i < suitable; i++) {
            MenuItem d = dish("Veg " + i, DietTag.VEGETARIAN, price, false);
            all.add(d);
            matching.add(d);
        }
        for (int i = 0; i < other; i++) {
            all.add(dish("Meat " + i, DietTag.MEAT_OR_FISH, price, false));
        }
        return new MenuReading(id, "https://" + id + ".example/menu", MenuKind.REGULAR, "es", "EUR", all, matching,
                false, false);
    }

    static PlaceReview review(double stars, String text, String publishTime) {
        return new PlaceReview(stars, text, null, "en", "X", publishTime, null);
    }

    /** Sorts reviews into positive and critical; {@code context} of them mention the request. */
    static ReviewInsight reviews(String id, List<PlaceReview> all, List<PlaceReview> context) {
        return new ReviewInsight(id, all.size(), all.stream().filter(r -> r.rating() >= 4).toList(),
                all.stream().filter(r -> r.rating() <= 3).toList(),
                context.stream().map(r -> new ContextReviewFinder.Match(r, List.of("vegetarian"))).toList());
    }

    static PlaceCandidate place(String id, double rating, int count, Set<Meal> meals) {
        return new PlaceCandidate(id, "Name " + id, null, "https://" + id + ".example/", null, rating, count, null,
                "OPERATIONAL", null, null, null, null, null, null, List.of(), meals, List.of());
    }
}
