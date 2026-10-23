package org.example.restaurantsuggestionagent.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GooglePlacesClientTest {

    private static final String URL = "https://places.test/v1/places:searchText";

    private MockRestServiceServer google;
    private GooglePlacesClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        google = MockRestServiceServer.bindTo(builder).build();
        client = new GooglePlacesClient(builder, "https://places.test", "test-key");
    }

    @Test
    void sendsKeyFieldMaskAndQueryThenMapsResponse() {
        google.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask", Matchers.containsString("places.websiteUri")))
                .andExpect(header("X-Goog-FieldMask", Matchers.containsString("places.servesVegetarianFood")))
                .andExpect(jsonPath("$.textQuery").value("vegetarian breakfast in Gràcia, Barcelona"))
                .andExpect(jsonPath("$.languageCode").value("en"))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.pageToken").doesNotExist())
                .andRespond(withSuccess(new ClassPathResource("places/search-page-1.json"), MediaType.APPLICATION_JSON));

        List<PlaceCandidate> places = client.search("vegetarian breakfast in Gràcia, Barcelona", "en", 2);

        google.verify();
        assertThat(places).hasSize(2);

        PlaceCandidate greenLeaf = places.get(0);
        assertThat(greenLeaf.id()).isEqualTo("place-green-leaf");
        assertThat(greenLeaf.name()).isEqualTo("Green Leaf Café");
        assertThat(greenLeaf.websiteUri()).isEqualTo("https://greenleaf.example/");
        assertThat(greenLeaf.rating()).isEqualTo(4.6);
        assertThat(greenLeaf.userRatingCount()).isEqualTo(1240);
        assertThat(greenLeaf.priceLevel()).isEqualTo("PRICE_LEVEL_MODERATE");
        assertThat(greenLeaf.servesBreakfast()).isTrue();
        assertThat(greenLeaf.servesDinner()).isFalse();
        assertThat(greenLeaf.openingHours().periods()).hasSize(1);
        assertThat(greenLeaf.openingHours().periods().get(0).open().hour()).isEqualTo(8);
        assertThat(greenLeaf.reviews()).singleElement().satisfies(r -> {
            assertThat(r.text()).isEqualTo("Great vegan pancakes, friendly staff.");
            assertThat(r.originalText()).startsWith("Tortitas veganas");
            assertThat(r.originalLanguage()).isEqualTo("es");
            assertThat(r.rating()).isEqualTo(5.0);
        });

        PlaceCandidate noWebsite = places.get(1);
        assertThat(noWebsite.websiteUri()).isNull();
        assertThat(noWebsite.servesVegetarianFood()).as("unknown must stay null, not false").isNull();
        assertThat(noWebsite.reviews()).isEmpty();
    }

    @Test
    void followsNextPageTokenUntilEnoughResults() {
        google.expect(requestTo(URL))
                .andExpect(jsonPath("$.pageSize").value(5))
                .andRespond(withSuccess(new ClassPathResource("places/search-page-1.json"), MediaType.APPLICATION_JSON));
        google.expect(requestTo(URL))
                .andExpect(jsonPath("$.pageToken").value("token-page-2"))
                .andExpect(jsonPath("$.pageSize").value(3))
                .andRespond(withSuccess(new ClassPathResource("places/search-page-2.json"), MediaType.APPLICATION_JSON));

        // Page 1 holds 2 places; asking for 5 means page 2 is requested for the remaining 3.
        List<PlaceCandidate> places = client.search("q", "en", 5);

        google.verify();
        assertThat(places).extracting(PlaceCandidate::id)
                .containsExactly("place-green-leaf", "place-no-website", "place-third");
    }

    @Test
    void googleErrorBecomesClearException() {
        google.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":403,\"message\":\"API key not valid.\"}}"));

        assertThatThrownBy(() -> client.search("q", "en", 20))
                .isInstanceOf(PlacesApiException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("API key not valid.");
    }

    @Test
    void missingKeyFailsBeforeAnyRequest() {
        GooglePlacesClient noKey = new GooglePlacesClient(RestClient.builder(), "https://places.test", "");

        assertThatThrownBy(() -> noKey.search("q", "en", 20))
                .isInstanceOf(PlacesApiException.class)
                .hasMessageContaining("GOOGLE_MAPS_KEY");
    }

    @Test
    void requestBodyOmitsNullFields() {
        google.expect(requestTo(URL))
                .andExpect(content().string(Matchers.not(Matchers.containsString("null"))))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.search("q", "en", 20)).isEmpty();
    }
}
