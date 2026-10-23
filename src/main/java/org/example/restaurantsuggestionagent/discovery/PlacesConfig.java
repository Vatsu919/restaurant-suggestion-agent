package org.example.restaurantsuggestionagent.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PlacesConfig {

    /** The key comes from the GOOGLE_MAPS_KEY environment variable and is checked on first use. */
    @Bean
    public PlaceSearch placeSearch(RestClient.Builder builder,
                                   @Value("${places.base-url:https://places.googleapis.com}") String baseUrl,
                                   @Value("${GOOGLE_MAPS_KEY:}") String apiKey) {
        return new GooglePlacesClient(builder, baseUrl, apiKey);
    }
}
