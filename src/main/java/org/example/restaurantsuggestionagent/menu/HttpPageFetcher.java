package org.example.restaurantsuggestionagent.menu;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;

/**
 * Fetches pages with Java's built-in HTTP client: follows redirects, times out, and
 * stops reading after {@code maxBytes} so one huge file can't exhaust memory.
 */
public class HttpPageFetcher implements PageFetcher {

    private final HttpClient client;
    private final String userAgent;
    private final Duration requestTimeout;
    private final int maxBytes;

    public HttpPageFetcher(String userAgent, Duration connectTimeout, Duration requestTimeout, int maxBytes) {
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(connectTimeout)
                .build();
        this.userAgent = userAgent;
        this.requestTimeout = requestTimeout;
        this.maxBytes = maxBytes;
    }

    @Override
    public FetchedPage fetch(String url) throws IOException {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(requestTimeout)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/pdf,image/*;q=0.8,*/*;q=0.5")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid URL", e);
        }
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                byte[] body = in.readNBytes(maxBytes + 1);
                boolean truncated = body.length > maxBytes;
                if (truncated) {
                    body = Arrays.copyOf(body, maxBytes);
                }
                return new FetchedPage(url, response.uri().toString(), response.statusCode(),
                        response.headers().firstValue("Content-Type").orElse(""), body, truncated);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
