package org.example.restaurantsuggestionagent.menu;

/**
 * A downloaded URL.
 *
 * @param finalUrl  where the request ended up after redirects
 * @param truncated true if the body was larger than the fetcher's size limit and was cut off
 */
public record FetchedPage(String requestedUrl, String finalUrl, int status, String contentType,
                          byte[] body, boolean truncated) {

    public boolean ok() {
        return status >= 200 && status < 300;
    }
}
