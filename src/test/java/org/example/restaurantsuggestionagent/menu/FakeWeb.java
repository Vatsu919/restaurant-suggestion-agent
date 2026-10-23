package org.example.restaurantsuggestionagent.menu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** An in-memory internet for tests: unknown URLs return 404. Records every fetch. */
class FakeWeb implements PageFetcher {

    private final Map<String, FetchedPage> pages = new HashMap<>();
    private final Set<String> unreachable = new HashSet<>();
    final List<String> fetched = new ArrayList<>();

    FakeWeb html(String url, String html) {
        pages.put(url, new FetchedPage(url, url, 200, "text/html; charset=utf-8",
                html.getBytes(StandardCharsets.UTF_8), false));
        return this;
    }

    /** A real PDF menu with prices. */
    FakeWeb pdf(String url) {
        return pdf(url, "Carta", "Hummus 8,50 €", "Falafel 9,00 €", "Ensalada 10,50 €", "Curry 13,00 €",
                "Tarta 6,50 €");
    }

    /** A PDF with a blank page and no text layer, like a scanned menu. */
    FakeWeb scannedPdf(String url) {
        return pdfBytes(url, makePdf());
    }

    /** A real PDF with these lines of text. */
    FakeWeb pdf(String url, String... lines) {
        return pdfBytes(url, makePdf(lines));
    }

    FakeWeb pdfBytes(String url, byte[] body) {
        // Served as octet-stream on purpose: the sniffer must recognise it from its bytes.
        pages.put(url, new FetchedPage(url, url, 200, "application/octet-stream", body, false));
        return this;
    }

    FakeWeb jpeg(String url) {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
        pages.put(url, new FetchedPage(url, url, 200, "image/jpeg", jpeg, false));
        return this;
    }

    FakeWeb text(String url, String body) {
        pages.put(url, new FetchedPage(url, url, 200, "text/plain", body.getBytes(StandardCharsets.UTF_8), false));
        return this;
    }

    FakeWeb down(String url) {
        unreachable.add(url);
        return this;
    }

    @Override
    public FetchedPage fetch(String url) throws IOException {
        fetched.add(url);
        if (unreachable.contains(url)) {
            throw new ConnectException("Connection refused");
        }
        FetchedPage page = pages.get(url);
        return page != null ? page : new FetchedPage(url, url, 404, "text/html", new byte[0], false);
    }

    /** Fetches excluding robots.txt lookups. */
    List<String> pageFetches() {
        return fetched.stream().filter(u -> !u.endsWith("/robots.txt")).toList();
    }

    private static byte[] makePdf(String... lines) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (lines.length > 0) {
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.setLeading(16);
                    content.newLineAtOffset(50, 700);
                    for (String line : lines) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A menu page body: enough prices for {@link MenuText#looksLikeMenu}. */
    static String menuHtml() {
        return "<html><body><h1>Carta</h1>"
                + "<p>Hummus 8,50 €</p><p>Falafel 9,00 €</p><p>Ensalada 10,50 €</p>"
                + "<p>Curry 13,00 €</p><p>Tarta 6,50 €</p></body></html>";
    }
}
