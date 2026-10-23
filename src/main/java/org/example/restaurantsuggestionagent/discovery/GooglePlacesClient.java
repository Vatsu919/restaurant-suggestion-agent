package org.example.restaurantsuggestionagent.discovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Calls Google Places API (New) Text Search and converts the response into
 * {@link PlaceCandidate}s. Google's JSON format does not leak outside this class.
 */
public class GooglePlacesClient implements PlaceSearch {

    static final String SEARCH_PATH = "/v1/places:searchText";

    /** Google returns at most 20 places per page. */
    static final int MAX_PAGE_SIZE = 20;

    /**
     * Only the fields later steps use; Places bills by the fields requested.
     * The serves* flags and reviews are in Google's higher-priced tier.
     */
    static final String FIELD_MASK = String.join(",",
            "places.id",
            "places.displayName",
            "places.formattedAddress",
            "places.websiteUri",
            "places.googleMapsUri",
            "places.rating",
            "places.userRatingCount",
            "places.priceLevel",
            "places.businessStatus",
            "places.primaryType",
            "places.servesBreakfast",
            "places.servesLunch",
            "places.servesDinner",
            "places.servesVegetarianFood",
            "places.regularOpeningHours",
            "places.reviews",
            "nextPageToken");

    private final RestClient http;
    private final String apiKey;

    public GooglePlacesClient(RestClient.Builder builder, String baseUrl, String apiKey) {
        this.http = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public List<PlaceCandidate> search(String textQuery, String languageCode, int maxResults) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new PlacesApiException("GOOGLE_MAPS_KEY is not set");
        }
        List<PlaceCandidate> results = new ArrayList<>();
        String pageToken = null;
        do {
            int pageSize = Math.min(MAX_PAGE_SIZE, maxResults - results.size());
            ApiSearchResponse page = fetchPage(new ApiSearchRequest(textQuery, languageCode, pageSize, pageToken));
            if (page == null || page.places() == null) {
                break;
            }
            page.places().stream().map(GooglePlacesClient::toCandidate).forEach(results::add);
            pageToken = page.nextPageToken();
        } while (pageToken != null && results.size() < maxResults);

        return results.size() > maxResults ? List.copyOf(results.subList(0, maxResults)) : List.copyOf(results);
    }

    private ApiSearchResponse fetchPage(ApiSearchRequest request) {
        return http.post()
                .uri(SEARCH_PATH)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", FIELD_MASK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new PlacesApiException("Google Places returned " + res.getStatusCode().value()
                            + ": " + errorMessage(res.getBody().readAllBytes()));
                })
                .body(ApiSearchResponse.class);
    }

    /** Google's error bodies are JSON; a short excerpt is enough for the user and the logs. */
    private static String errorMessage(byte[] body) throws IOException {
        String text = new String(body, StandardCharsets.UTF_8).replaceAll("\\s+", " ").strip();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    static PlaceCandidate toCandidate(ApiPlace p) {
        return new PlaceCandidate(
                p.id(),
                p.displayName() == null ? null : p.displayName().text(),
                p.formattedAddress(),
                p.websiteUri(),
                p.googleMapsUri(),
                p.rating(),
                p.userRatingCount(),
                p.priceLevel(),
                p.businessStatus(),
                p.primaryType(),
                p.servesBreakfast(),
                p.servesLunch(),
                p.servesDinner(),
                p.servesVegetarianFood(),
                p.regularOpeningHours(),
                p.reviews() == null ? List.of() : p.reviews().stream().map(GooglePlacesClient::toReview).toList(),
                Set.of(),
                List.of());
    }

    private static PlaceReview toReview(ApiReview r) {
        return new PlaceReview(
                r.rating(),
                r.text() == null ? null : r.text().text(),
                r.originalText() == null ? null : r.originalText().text(),
                r.originalText() == null ? null : r.originalText().languageCode(),
                r.authorAttribution() == null ? null : r.authorAttribution().displayName(),
                r.publishTime(),
                r.relativePublishTimeDescription());
    }

    // --- Google's wire format (Places API New). Package-private: nothing outside uses these. ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ApiSearchRequest(String textQuery, String languageCode, Integer pageSize, String pageToken) {
    }

    record ApiSearchResponse(List<ApiPlace> places, String nextPageToken) {
    }

    record ApiPlace(
            String id,
            ApiText displayName,
            String formattedAddress,
            String websiteUri,
            String googleMapsUri,
            Double rating,
            Integer userRatingCount,
            String priceLevel,
            String businessStatus,
            String primaryType,
            Boolean servesBreakfast,
            Boolean servesLunch,
            Boolean servesDinner,
            Boolean servesVegetarianFood,
            OpeningHours regularOpeningHours,
            List<ApiReview> reviews) {
    }

    record ApiText(String text, String languageCode) {
    }

    record ApiReview(
            Double rating,
            ApiText text,
            ApiText originalText,
            ApiAuthor authorAttribution,
            String publishTime,
            String relativePublishTimeDescription) {
    }

    record ApiAuthor(String displayName, String uri) {
    }
}
