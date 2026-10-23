package org.example.restaurantsuggestionagent.extraction;

/** What kind of menu a page or PDF turned out to be; only REGULAR menus are used. */
public enum MenuKind {
    /** The restaurant's standing food menu, including a daily fixed-price lunch menu that names its dishes. */
    REGULAR,
    /** Menus for groups, events, celebrations, companies or students. */
    GROUP_OR_EVENT,
    /** Dishes of the day or week that change often, or set-menu prices without the dishes. */
    DAILY_SPECIALS,
    /** Only drinks: wine, cocktails, coffee. */
    DRINKS_ONLY,
    /** No dishes at all (a brochure, an about page), or a shop page for one or two products. */
    NOT_A_MENU
}
