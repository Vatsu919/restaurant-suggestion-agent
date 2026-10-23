package org.example.restaurantsuggestionagent.menu;

/**
 * A restaurant's menu as found on its website, already downloaded so the extraction
 * step doesn't fetch it again.
 *
 * @param url      the menu page or file the user can open
 * @param foundVia how it was found, e.g. "link “Carta” on the homepage"
 */
public record MenuSource(String url, MenuType type, String contentType, byte[] content, String foundVia) {

    @Override
    public String toString() {
        return "MenuSource[" + type + " " + url + ", " + content.length + " bytes, via " + foundVia + "]";
    }
}
