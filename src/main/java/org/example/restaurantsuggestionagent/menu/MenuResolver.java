package org.example.restaurantsuggestionagent.menu;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.restaurantsuggestionagent.agent.SkipReason;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Finds one restaurant's menu on its website:
 * <ol>
 *   <li>respect robots.txt and fetch the homepage;</li>
 *   <li>try the homepage's menu-looking links, best first; an HTML page without prices
 *       is searched one level deeper, including PDFs embedded in its code;</li>
 *   <li>accept the homepage itself if it lists prices;</li>
 *   <li>try common paths such as /menu and /carta.</li>
 * </ol>
 * Only HTML pages and PDFs are accepted, and both must list prices: a PDF without prices
 * is a brochure, a leaflet or similar, not a menu. Image menus (including scanned PDFs)
 * are recognised but not read, so the skip reason can say the menu exists only as images. At most {@code maxFetches} pages
 * are downloaded per restaurant.
 */
public class MenuResolver {

    static final List<String> COMMON_PATHS = List.of(
            "/menu", "/carta", "/menus", "/la-carta", "/en/menu", "/es/carta", "/menu.pdf", "/carta.pdf");

    /** Below this much visible text, a homepage probably needs JavaScript to show anything. */
    private static final int MIN_VISIBLE_TEXT = 200;

    /** Below this much text, a PDF is scanned pages (images) rather than text. */
    private static final int MIN_PDF_TEXT = 50;

    /** What a downloaded PDF turned out to be. */
    private enum PdfKind {
        MENU("PDF with prices"),
        NO_PRICES("PDF has no prices"),
        SCANNED("scanned PDF (images, not read)"),
        UNREADABLE("PDF can't be read");

        final String outcome;

        PdfKind(String outcome) {
            this.outcome = outcome;
        }
    }

    private final PageFetcher fetcher;
    private final RobotsPolicy robots;
    private final int maxFetches;

    public MenuResolver(PageFetcher fetcher, RobotsPolicy robots, int maxFetches) {
        this.fetcher = fetcher;
        this.robots = robots;
        this.maxFetches = maxFetches;
    }

    private record Candidate(String url, String foundVia, int depth) {
    }

    /** Mutable state of one search: what's been tried, and how many fetches remain. */
    private final class Search {
        final PlaceCandidate place;
        final List<String> attempts = new ArrayList<>();
        final Set<String> tried = new HashSet<>();
        int fetches;
        /** First page or URL where the menu appeared as images; reported if nothing readable is found. */
        String imageMenuAt;

        void sawImageMenu(String where) {
            if (imageMenuAt == null) {
                imageMenuAt = where;
            }
        }

        Search(PlaceCandidate place) {
            this.place = place;
        }
    }

    public MenuResolution resolve(PlaceCandidate place) {
        Search search = new Search(place);
        String home = place.websiteUri();

        if (!robots.allows(home)) {
            return MenuResolution.notFound(SkipReason.BLOCKED_BY_ROBOTS,
                    "The website's robots.txt doesn't allow automated access", search.attempts);
        }
        FetchedPage homePage;
        try {
            homePage = fetcher.fetch(home);
            search.fetches++;
        } catch (IOException e) {
            search.attempts.add(home + " → " + describe(e));
            return MenuResolution.notFound(SkipReason.WEBSITE_UNREACHABLE,
                    "The website couldn't be reached (" + describe(e) + ")", search.attempts);
        }
        search.tried.add(normalize(home));
        search.tried.add(normalize(homePage.finalUrl()));
        if (!homePage.ok()) {
            search.attempts.add(home + " → HTTP " + homePage.status());
            return MenuResolution.notFound(SkipReason.WEBSITE_UNREACHABLE,
                    "The website returned HTTP " + homePage.status(), search.attempts);
        }

        MenuType homeType = ContentSniffer.typeOf(homePage);
        if (homeType == MenuType.PDF) {
            PdfKind kind = checkPdf(homePage);
            search.attempts.add(home + " → " + kind.outcome);
            return switch (kind) {
                case MENU -> found(homePage, homeType, "the website address itself (a PDF with prices)", search);
                case NO_PRICES -> MenuResolution.notFound(SkipReason.MENU_NOT_FOUND,
                        "The website address is a PDF without prices, not a menu", search.attempts);
                case SCANNED -> MenuResolution.notFound(SkipReason.MENU_NOT_FOUND,
                        "The website address is a scanned PDF with no readable text", search.attempts);
                case UNREADABLE -> MenuResolution.notFound(SkipReason.MENU_NOT_FOUND,
                        "The website address is a PDF that can't be read", search.attempts);
            };
        }
        if (homeType == MenuType.IMAGE) {
            search.attempts.add(home + " → image");
            return MenuResolution.notFound(SkipReason.MENU_IS_IMAGE,
                    "The website address is an image of the menu; image menus aren't read", search.attempts);
        }
        if (homeType != MenuType.HTML) {
            search.attempts.add(home + " → not a web page (" + homePage.contentType() + ")");
            return MenuResolution.notFound(SkipReason.MENU_NOT_FOUND,
                    "The website isn't a normal web page", search.attempts);
        }

        Document homeDoc = parse(homePage);
        String homeText = homeDoc.body() == null ? "" : homeDoc.body().text();
        search.attempts.add(home + " → homepage");

        // 1. Menu links on the homepage.
        Deque<Candidate> queue = new ArrayDeque<>();
        for (MenuLinkFinder.Link link : MenuLinkFinder.find(homeDoc, place.matchedMeals())) {
            queue.add(new Candidate(link.url(), "link “" + shorten(link.label()) + "” on the homepage", 1));
        }
        Optional<MenuResolution> result = tryAll(queue, search);
        if (result.isPresent()) {
            return result.get();
        }

        // 2. A single-page website with the menu on the homepage.
        if (MenuText.looksLikeMenu(homeText)) {
            return found(homePage, MenuType.HTML, "the homepage (it lists prices)", search);
        }

        // 3. Common paths.
        String origin = originOf(homePage.finalUrl());
        for (String path : COMMON_PATHS) {
            queue.add(new Candidate(origin + path, "common path " + path, 1));
        }
        result = tryAll(queue, search);
        if (result.isPresent()) {
            return result.get();
        }

        if (search.imageMenuAt == null && MenuLinkFinder.hasMenuImages(homeDoc)) {
            search.sawImageMenu(home);
        }
        if (search.imageMenuAt != null) {
            return MenuResolution.notFound(SkipReason.MENU_IS_IMAGE,
                    "The menu is only published as images (on " + pathOf(search.imageMenuAt)
                            + "); image menus aren't read", search.attempts);
        }

        String detail = homeText.strip().length() < MIN_VISIBLE_TEXT
                ? "No menu found; the website seems to need JavaScript to show its content"
                : "No menu found on the website";
        return MenuResolution.notFound(SkipReason.MENU_NOT_FOUND, detail, search.attempts);
    }

    private Optional<MenuResolution> tryAll(Deque<Candidate> queue, Search search) {
        while (!queue.isEmpty() && search.fetches < maxFetches) {
            Candidate c = queue.poll();
            if (!search.tried.add(normalize(c.url()))) {
                continue;
            }
            if (MenuLinkFinder.isImagePath(c.url())) {
                // Not downloaded: image menus aren't read.
                search.attempts.add(c.url() + " → image (not read)");
                search.sawImageMenu(c.url());
                continue;
            }
            if (!robots.allows(c.url())) {
                search.attempts.add(c.url() + " → blocked by robots.txt");
                continue;
            }
            FetchedPage page;
            try {
                page = fetcher.fetch(c.url());
                search.fetches++;
            } catch (IOException e) {
                search.fetches++;
                search.attempts.add(c.url() + " → " + describe(e));
                continue;
            }
            if (!page.ok()) {
                search.attempts.add(c.url() + " → HTTP " + page.status());
                continue;
            }
            if (page.truncated()) {
                search.attempts.add(c.url() + " → file too large");
                continue;
            }

            MenuType type = ContentSniffer.typeOf(page);
            if (type == MenuType.PDF) {
                PdfKind kind = checkPdf(page);
                search.attempts.add(c.url() + " → " + kind.outcome);
                if (kind == PdfKind.MENU) {
                    return Optional.of(found(page, type, c.foundVia(), search));
                }
                if (kind == PdfKind.SCANNED) {
                    search.sawImageMenu(c.url());
                }
                continue;
            }
            if (type == MenuType.IMAGE) {
                search.attempts.add(c.url() + " → image (not read)");
                search.sawImageMenu(c.url());
                continue;
            }
            if (type != MenuType.HTML) {
                search.attempts.add(c.url() + " → not a menu (" + page.contentType() + ")");
                continue;
            }

            Document doc = parse(page);
            if (MenuText.looksLikeMenu(doc.body() == null ? "" : doc.body().text())) {
                search.attempts.add(c.url() + " → page with prices");
                return Optional.of(found(page, MenuType.HTML, c.foundVia(), search));
            }
            search.attempts.add(c.url() + " → page has no prices");
            if (MenuLinkFinder.hasMenuImages(doc)) {
                search.sawImageMenu(page.finalUrl());
            }

            // A "Menus" page that only links to the real menus: look one level deeper, first.
            // Queue order afterwards: its menu links, then PDFs embedded in its code, then the rest.
            if (c.depth() < 2) {
                String on = pathOf(page.finalUrl());
                List<Candidate> deeper = new ArrayList<>();
                for (MenuLinkFinder.Link link : MenuLinkFinder.find(doc, search.place.matchedMeals())) {
                    deeper.add(new Candidate(link.url(), "link “" + shorten(link.label()) + "” on " + on, 2));
                }
                for (MenuLinkFinder.Link pdf : MenuLinkFinder.embeddedPdfs(doc)) {
                    deeper.add(new Candidate(pdf.url(), "PDF embedded in " + on, 2));
                }
                for (int i = deeper.size() - 1; i >= 0; i--) {
                    queue.addFirst(deeper.get(i));
                }
            }
        }
        return Optional.empty();
    }

    private static MenuResolution found(FetchedPage page, MenuType type, String foundVia, Search search) {
        return MenuResolution.found(
                new MenuSource(page.finalUrl(), type, page.contentType(), page.body(), foundVia), search.attempts);
    }

    /** The same rule as for HTML pages: a menu lists prices. */
    private static PdfKind checkPdf(FetchedPage page) {
        String text;
        try {
            text = PdfText.extract(page.body());
        } catch (IOException | RuntimeException e) { // damaged, password-protected, or not really a PDF
            return PdfKind.UNREADABLE;
        }
        if (text.strip().length() < MIN_PDF_TEXT) {
            return PdfKind.SCANNED;
        }
        return MenuText.looksLikeMenu(text) ? PdfKind.MENU : PdfKind.NO_PRICES;
    }

    private static Document parse(FetchedPage page) {
        try {
            // Charset detected from the page's headers/meta tags.
            return Jsoup.parse(new ByteArrayInputStream(page.body()), null, page.finalUrl());
        } catch (IOException e) {
            return Jsoup.parse("", page.finalUrl());
        }
    }

    private static String describe(IOException e) {
        String name = e.getClass().getSimpleName();
        if (name.contains("Timeout")) {
            return "timed out";
        }
        if (name.contains("UnknownHost") || name.contains("ConnectException")) {
            return "could not connect";
        }
        return e.getMessage() == null ? name : e.getMessage();
    }

    private static String normalize(String url) {
        String u = MenuLinkFinder.stripFragment(url);
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private static String originOf(String url) {
        URI uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }

    private static String pathOf(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null || path.isEmpty() ? "/" : path;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private static String shorten(String label) {
        String s = label.replaceAll("\\s+", " ").strip();
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
