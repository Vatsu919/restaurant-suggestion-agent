package org.example.restaurantsuggestionagent.menu;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.restaurantsuggestionagent.query.Meal;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * Scores the links on a page by how likely they lead to the food menu, using the link
 * text, title, image alt text and URL path, in several European languages.
 */
final class MenuLinkFinder {

    /** Links below this score are not tried. One menu word is enough. */
    static final int MIN_SCORE = 10;

    record Link(String url, String label, int score, boolean isDocument) {
    }

    private static final Set<String> MENU_WORDS = Set.of(
            "menu", "menus", "carta", "cartas", "carte", "cartes", "speisekarte", "karte", "menukaart", "kaart",
            "ementa", "cardapio", "food", "eat", "brunch", "breakfast", "lunch", "dinner",
            "desayuno", "desayunos", "esmorzar", "esmorzars", "almuerzo", "comida", "cena", "cenas", "sopar", "dinar",
            "fruhstuck", "mittagessen", "abendessen", "colazione", "pranzo", "dejeuner", "diner");

    /** Any of these rules a link out: it's about something other than the regular food menu. */
    private static final Set<String> NOT_FOOD_MENU_WORDS = Set.of(
            "vino", "vinos", "vins", "vin", "wine", "wines", "wein", "cocktail", "cocktails", "cocteles", "drinks",
            "bebidas", "begudes", "getranke", "allergen", "allergens", "alergenos", "alergens", "allergenes",
            "grupo", "grupos", "grup", "grups", "group", "groups", "estudiant", "estudiants", "estudiante", "estudiantes",
            "student", "students", "evento", "eventos", "events", "empresa", "empresas", "celebraciones", "catering",
            "reserva", "reservas", "reservar", "reserve", "reservation", "reservations", "booking", "book",
            "gift", "voucher", "vouchers", "regalo", "jobs", "careers", "trabaja", "empleo", "contact", "contacto",
            "privacy", "privacidad", "cookies", "legal", "aviso");

    private static final Map<Meal, Set<String>> MEAL_WORDS = Map.of(
            Meal.BREAKFAST, Set.of("breakfast", "brunch", "desayuno", "desayunos", "esmorzar", "esmorzars",
                    "fruhstuck", "colazione"),
            Meal.LUNCH, Set.of("lunch", "almuerzo", "dinar", "mittagessen", "pranzo", "dejeuner"),
            Meal.DINNER, Set.of("dinner", "cena", "cenas", "sopar", "abendessen", "diner"));

    /**
     * Social media, maps and delivery apps: not the restaurant's own menu, or prices are marked up.
     * Entries ending in "." match any country domain (tripadvisor.es, tripadvisor.co.uk …).
     */
    private static final List<String> EXCLUDED_HOSTS = List.of(
            "instagram.com", "facebook.com", "tiktok.com", "twitter.com", "x.com", "tripadvisor.",
            "maps.google.", "maps.app.goo.gl", "glovoapp.com", "ubereats.com", "deliveroo.", "just-eat.", "justeat.");

    /** Words that name the menu itself; used for images, where "brunch" or "food" usually means a photo. */
    private static final Set<String> MENU_NOUNS = Set.of(
            "menu", "menus", "carta", "cartas", "carte", "speisekarte", "karte", "menukaart", "ementa", "cardapio");

    /** An absolute PDF address anywhere in a page's code, including inside scripts. */
    private static final Pattern PDF_URL = Pattern.compile(
            "https?://[^\\s\"'<>()\\\\]+?\\.pdf(?:\\?[^\\s\"'<>()\\\\]*)?", Pattern.CASE_INSENSITIVE);

    private MenuLinkFinder() {
    }

    /**
     * PDFs referenced anywhere in the page, not only through links — e.g. opened by a
     * script button. Only used on pages already reached through a menu link, so a PDF
     * needs no menu word in its name, but exclusion words (allergens, groups…) still apply.
     */
    static List<Link> embeddedPdfs(Document page) {
        String code = page.html().replace("\\/", "/"); // URLs inside JSON are often written as https:\/\/…
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = PDF_URL.matcher(code);
        while (m.find()) {
            String url = Parser.unescapeEntities(m.group(), true);
            if (!isExcluded(url)) {
                seen.add(url);
            }
        }
        List<Link> links = new ArrayList<>();
        for (String url : seen) {
            Link link = score(url, "", Set.of());
            if (link.score() >= 0) {
                links.add(link);
            }
        }
        return links;
    }

    /** True if the page shows images that look like the menu itself (by file name, alt or title text). */
    static boolean hasMenuImages(Document page) {
        for (Element img : page.select("img")) {
            String described = img.attr("src") + " " + img.attr("data-src") + " " + img.attr("alt") + " "
                    + img.attr("title");
            List<String> words = MenuText.words(described);
            if (words.stream().anyMatch(MENU_NOUNS::contains)
                    && words.stream().noneMatch(NOT_FOOD_MENU_WORDS::contains)) {
                return true;
            }
        }
        return false;
    }

    static boolean isImagePath(String url) {
        String p = pathOf(url).toLowerCase(Locale.ROOT);
        return p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") || p.endsWith(".webp")
                || p.endsWith(".gif");
    }

    /** Menu-looking links on {@code page}, best first, without duplicates or links back to the page itself. */
    static List<Link> find(Document page, Set<Meal> meals) {
        String self = stripFragment(page.location());
        Map<String, Link> best = new LinkedHashMap<>();
        for (Element a : page.select("a[href]")) {
            String url = stripFragment(a.absUrl("href"));
            if (!url.startsWith("http://") && !url.startsWith("https://") || url.equals(self) || isExcluded(url)) {
                continue;
            }
            String label = (a.text() + " " + a.attr("title") + " " + a.attr("aria-label") + " "
                    + String.join(" ", a.select("img[alt]").eachAttr("alt"))).strip();
            Link link = score(url, label, meals);
            if (link.score() >= MIN_SCORE) {
                best.merge(url, link, (x, y) -> x.score() >= y.score() ? x : y);
            }
        }
        List<Link> links = new ArrayList<>(best.values());
        links.sort(Comparator.comparingInt(Link::score).reversed()); // stable: page order breaks ties
        return links;
    }

    static Link score(String url, String label, Set<Meal> meals) {
        String path = pathOf(url);
        List<String> words = new ArrayList<>(MenuText.words(label));
        words.addAll(MenuText.words(path));
        boolean isDocument = isDocumentPath(path);
        String shownLabel = label.isEmpty() ? path : label;

        // "Carta de alérgenos", "menu-per-a-grups": a menu word doesn't make these the food menu.
        if (words.stream().anyMatch(NOT_FOOD_MENU_WORDS::contains)) {
            return new Link(url, shownLabel, -1, isDocument);
        }

        int menuHits = (int) words.stream().filter(MENU_WORDS::contains).distinct().count();
        int score = Math.min(menuHits, 2) * 10;
        if (path.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            score += 5;
        }
        for (Meal meal : meals) {
            if (words.stream().anyMatch(MEAL_WORDS.get(meal)::contains)) {
                score += 3;
                break;
            }
        }
        return new Link(url, shownLabel, score, isDocument);
    }

    private static boolean isDocumentPath(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".pdf") || p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png")
                || p.endsWith(".webp");
    }

    private static boolean isExcluded(String url) {
        String host = hostOf(url);
        return EXCLUDED_HOSTS.stream().anyMatch(h -> h.endsWith(".")
                ? host.startsWith(h) || host.contains("." + h)
                : host.equals(h) || host.endsWith("." + h));
    }

    static String stripFragment(String url) {
        int hash = url.indexOf('#');
        return hash >= 0 ? url.substring(0, hash) : url;
    }

    private static String pathOf(String url) {
        try {
            URI uri = URI.create(url);
            return (uri.getPath() == null ? "" : uri.getPath()) + (uri.getQuery() == null ? "" : " " + uri.getQuery());
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
