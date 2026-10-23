package org.example.restaurantsuggestionagent.menu;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text helpers shared by menu discovery: price detection and word splitting. */
final class MenuText {

    /** A price with a currency: "12,50 €", "€9", "£8.50", "14 EUR", "9.90 CHF". */
    private static final Pattern PRICE_WITH_CURRENCY = Pattern.compile(
            "(?:[€£]\\s?\\d{1,3}(?:[.,]\\d{1,2})?)"
                    + "|(?:\\d{1,3}(?:[.,]\\d{1,2})?\\s?(?:€|£|eur\\b|euros?\\b|chf\\b|kr\\b))",
            Pattern.CASE_INSENSITIVE);

    /** A bare amount with two decimals, as menus often omit the currency: "12,50". */
    private static final Pattern DECIMAL_AMOUNT = Pattern.compile("\\b\\d{1,3}[.,]\\d{2}\\b");

    static final int MIN_PRICES_WITH_CURRENCY = 5;
    static final int MIN_DECIMAL_AMOUNTS = 8;

    private MenuText() {
    }

    /** True if the text has enough prices to be a menu rather than, say, a page of links. */
    static boolean looksLikeMenu(String text) {
        return count(PRICE_WITH_CURRENCY, text) >= MIN_PRICES_WITH_CURRENCY
                || count(DECIMAL_AMOUNT, text) >= MIN_DECIMAL_AMOUNTS;
    }

    /**
     * Lower-case words without accents, with camelCase split:
     * "La Carta — Menú" → [la, carta, menu]; "Oassis-MenusGrup.pdf" → [oassis, menus, grup, pdf].
     */
    static List<String> words(String text) {
        String plain = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("(\\p{Ll})(\\p{Lu})", "$1 $2")
                .toLowerCase(Locale.ROOT);
        return Arrays.stream(plain.split("[^a-z0-9]+")).filter(w -> !w.isEmpty()).toList();
    }

    private static int count(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text == null ? "" : text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
