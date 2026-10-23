package org.example.restaurantsuggestionagent.menu;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * Turns an HTML menu page into plain text, in Java, so only text (not markup, scripts
 * or navigation) is sent to the LLM. Line breaks are kept: a dish and its price on one
 * line, the next dish on the next. PDF menus are sent to the LLM as they are.
 */
public final class MenuTextExtractor {

    /** Parts of a page that are never menu content. */
    private static final String NOISE = "script, style, noscript, svg, iframe, template, nav, footer";

    private MenuTextExtractor() {
    }

    public static String htmlText(MenuSource menu) throws IOException {
        Document doc = Jsoup.parse(new ByteArrayInputStream(menu.content()), null, menu.url());
        doc.select(NOISE).remove();
        if (doc.body() == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode text) {
                    out.append(text.text());
                } else if (node instanceof Element e && !isCell(e)
                        && (e.isBlock() || e.normalName().equals("br"))) {
                    out.append('\n');
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element e) {
                    if (isCell(e)) {
                        out.append(' '); // table cells: dish and price stay on one line
                    } else if (e.isBlock()) {
                        out.append('\n');
                    }
                }
            }
        }, doc.body());
        return tidy(out.toString());
    }

    private static boolean isCell(Element e) {
        return e.normalName().equals("td") || e.normalName().equals("th");
    }

    /** Trims each line, collapses runs of spaces and drops empty lines. */
    static String tidy(String text) {
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\\R")) {
            String clean = line.replaceAll("[\\s\\u00A0]+", " ").strip();
            if (!clean.isEmpty()) {
                out.append(clean).append('\n');
            }
        }
        return out.toString();
    }
}
