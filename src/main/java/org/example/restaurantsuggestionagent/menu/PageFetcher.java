package org.example.restaurantsuggestionagent.menu;

import java.io.IOException;

/** Downloads a URL. Non-2xx responses are returned, not thrown; network failures throw. */
public interface PageFetcher {

    FetchedPage fetch(String url) throws IOException;
}
