package org.example.restaurantsuggestionagent.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parsed robots.txt rules for one site, following the standard (RFC 9309):
 * the group naming our user agent wins over "*", the longest matching rule wins,
 * Allow wins a tie, and "*" / "$" wildcards are supported.
 */
final class RobotsRules {

    static final RobotsRules ALLOW_ALL = new RobotsRules(List.of());

    private record Rule(boolean allow, String pattern, Pattern regex) {
    }

    private final List<Rule> rules;

    private RobotsRules(List<Rule> rules) {
        this.rules = rules;
    }

    static RobotsRules parse(String robotsTxt, String agentToken) {
        String agent = agentToken.toLowerCase(Locale.ROOT);
        List<Rule> forStar = new ArrayList<>();
        List<Rule> forUs = new ArrayList<>();
        boolean ourGroupExists = false;

        List<String> groupAgents = new ArrayList<>();
        boolean previousLineWasAgent = false;
        for (String raw : robotsTxt.split("\\R")) {
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).strip();
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();

            if (key.equals("user-agent")) {
                if (!previousLineWasAgent) {
                    groupAgents.clear(); // consecutive user-agent lines share one group
                }
                groupAgents.add(value.toLowerCase(Locale.ROOT));
                ourGroupExists |= value.equalsIgnoreCase(agent);
                previousLineWasAgent = true;
            } else if (key.equals("allow") || key.equals("disallow")) {
                previousLineWasAgent = false;
                if (value.isEmpty()) {
                    continue; // "Disallow:" with no path allows everything
                }
                Rule rule = new Rule(key.equals("allow"), value, toRegex(value));
                if (groupAgents.contains("*")) {
                    forStar.add(rule);
                }
                if (groupAgents.contains(agent)) {
                    forUs.add(rule);
                }
            } else {
                previousLineWasAgent = false;
            }
        }
        return new RobotsRules(List.copyOf(ourGroupExists ? forUs : forStar));
    }

    /** @param pathAndQuery e.g. "/menu?lang=en" */
    boolean allows(String pathAndQuery) {
        Rule best = null;
        for (Rule rule : rules) {
            if (!rule.regex().matcher(pathAndQuery).lookingAt()) {
                continue;
            }
            if (best == null
                    || rule.pattern().length() > best.pattern().length()
                    || (rule.pattern().length() == best.pattern().length() && rule.allow())) {
                best = rule;
            }
        }
        return best == null || best.allow();
    }

    private static Pattern toRegex(String pattern) {
        boolean anchoredEnd = pattern.endsWith("$");
        String body = anchoredEnd ? pattern.substring(0, pattern.length() - 1) : pattern;
        StringBuilder regex = new StringBuilder();
        for (String part : body.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        if (anchoredEnd) {
            regex.append("$");
        }
        return Pattern.compile(regex.toString());
    }
}
