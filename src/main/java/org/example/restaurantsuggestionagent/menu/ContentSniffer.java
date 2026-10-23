package org.example.restaurantsuggestionagent.menu;

import java.util.Locale;

/**
 * Decides what a downloaded file is. Checks the first bytes as well as the
 * Content-Type header, because many sites serve PDFs as application/octet-stream.
 */
final class ContentSniffer {

    private ContentSniffer() {
    }

    static MenuType typeOf(FetchedPage page) {
        byte[] b = page.body();
        String type = page.contentType() == null ? "" : page.contentType().toLowerCase(Locale.ROOT);

        if (startsWith(b, "%PDF") || type.contains("application/pdf")) {
            return MenuType.PDF;
        }
        if (isJpeg(b) || isPng(b) || isWebp(b) || startsWith(b, "GIF8") || type.startsWith("image/")) {
            return MenuType.IMAGE;
        }
        if (type.contains("html") || looksLikeMarkup(b)) {
            return MenuType.HTML;
        }
        return MenuType.OTHER;
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean isWebp(byte[] b) {
        return b.length > 11 && startsWith(b, "RIFF") && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private static boolean looksLikeMarkup(byte[] b) {
        for (int i = 0; i < Math.min(b.length, 512); i++) {
            int c = b[i] & 0xFF;
            if (c == '<') {
                return true;
            }
            // Skip whitespace and a UTF-8 byte-order mark before the first tag.
            if (!Character.isWhitespace(c) && c != 0xEF && c != 0xBB && c != 0xBF) {
                return false;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] b, String prefix) {
        if (b.length < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (b[i] != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
