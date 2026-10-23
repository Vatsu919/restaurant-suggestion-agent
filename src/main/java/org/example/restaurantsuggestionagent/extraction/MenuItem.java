package org.example.restaurantsuggestionagent.extraction;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import org.example.restaurantsuggestionagent.query.Meal;

/** One dish as read from the menu; part of the LLM's structured answer. */
public record MenuItem(
        @JsonPropertyDescription("The dish name exactly as written on the menu.")
        String originalName,

        @JsonPropertyDescription("The dish name translated into the user's language; the same as originalName "
                + "if the menu is already in that language.")
        String translatedName,

        @JsonPropertyDescription("A short description in the user's language, from the menu's own text only. "
                + "Empty if the menu gives none.")
        String description,

        @JsonPropertyDescription("The menu section in the user's language, e.g. \"Starters\", \"Brunch\". "
                + "Empty if the menu has no sections.")
        String section,

        @JsonPropertyDescription("Meals the menu serves this dish at, from its section or the times given, "
                + "e.g. a \"Brunch\" section → [BREAKFAST], \"Menú del día\" → [LUNCH]. "
                + "Empty if the menu doesn't tie the dish to a meal (served all day).")
        List<Meal> meals,

        @JsonPropertyDescription("The price exactly as written, e.g. \"12,50 €\". Empty if no price is given.")
        String priceText,

        @JsonPropertyDescription("The price as a number, e.g. 12.5. 0 if no price is given.")
        double price,

        @JsonPropertyDescription("VEGAN, VEGETARIAN, MEAT_OR_FISH, or UNCLEAR when the menu doesn't say enough. "
                + "Use menu labels (V, VG, leaf icons described in text) and the ingredients listed. "
                + "Don't guess from the dish name alone when ingredients aren't given.")
        DietTag diet,

        @JsonPropertyDescription("Whether the dish contains egg, from the ingredients listed. UNKNOWN if not stated.")
        EggContent egg,

        @JsonPropertyDescription("If the dish contains something the user wants to avoid, name it, "
                + "e.g. \"mushrooms\". Empty otherwise.")
        String containsAvoided,

        @JsonPropertyDescription("True if the dish is one of the dishes or cuisines the user likes, "
                + "e.g. a tapa for \"tapas\", a spicy dish for \"spicy food\". False if the user named none.")
        boolean matchesPreferences) {

    public MenuItem {
        meals = meals == null ? List.of() : List.copyOf(meals);
    }

    /** True if the dish is served at {@code meal}; a dish not tied to any meal is served at all of them. */
    public boolean servedAt(Meal meal) {
        return meals.isEmpty() || meals.contains(meal);
    }
}
