package org.example.restaurantsuggestionagent.agent;

/** Why a restaurant was dropped. Each step adds the reasons it can produce. */
public enum SkipReason {
    // FILTER
    PERMANENTLY_CLOSED,
    TEMPORARILY_CLOSED,
    NO_WEBSITE,
    LOW_RATING,
    NOT_SERVING_MEAL,
    NOT_OPEN_FOR_MEAL,

    // RESOLVE_MENU
    BLOCKED_BY_ROBOTS,
    WEBSITE_UNREACHABLE,
    MENU_NOT_FOUND,
    /** The menu exists but only as images; only HTML and PDF menus are read. */
    MENU_IS_IMAGE,

    // EXTRACT_MENU
    /** The menu file or page has no text that can be read, or the LLM couldn't read it. */
    MENU_UNREADABLE,
    /** The menu found is for groups, events, daily specials or drinks, not the regular food menu. */
    NOT_REGULAR_MENU,
    NO_MENU_ITEMS,
    /** No dish suits the user's diet (or every suitable dish contains something they avoid). */
    NO_MATCHING_ITEMS
}
