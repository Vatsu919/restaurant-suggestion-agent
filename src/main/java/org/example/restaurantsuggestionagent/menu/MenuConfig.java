package org.example.restaurantsuggestionagent.menu;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MenuConfig {

    /** Product token in our User-Agent; also the name robots.txt rules may address us by. */
    static final String AGENT_TOKEN = "RestaurantSuggestionAgent";

    @Bean
    public PageFetcher pageFetcher(@Value("${menu.max-download-mb:15}") int maxDownloadMb,
                                   @Value("${menu.request-timeout-seconds:20}") int timeoutSeconds) {
        return new HttpPageFetcher(AGENT_TOKEN + "/0.1 (menu lookup for a travel recommendation demo)",
                Duration.ofSeconds(5), Duration.ofSeconds(timeoutSeconds), maxDownloadMb * 1024 * 1024);
    }

    @Bean
    public MenuResolver menuResolver(PageFetcher fetcher,
                                     @Value("${menu.max-fetches-per-restaurant:8}") int maxFetches) {
        return new MenuResolver(fetcher, new RobotsPolicy(fetcher, AGENT_TOKEN), maxFetches);
    }

    /** Restaurants whose websites are checked at the same time. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService menuExecutor(@Value("${menu.parallel-restaurants:6}") int parallel) {
        return Executors.newFixedThreadPool(parallel);
    }
}
