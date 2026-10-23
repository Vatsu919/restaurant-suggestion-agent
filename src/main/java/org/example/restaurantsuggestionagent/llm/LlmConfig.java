package org.example.restaurantsuggestionagent.llm;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class LlmConfig {

    /**
     * Reads ANTHROPIC_API_KEY from Spring's environment: an environment variable, or the
     * project's .env file (imported in application.properties). The SDK's own fromEnv()
     * only sees real environment variables, so the key is passed to it explicitly. Lazy so
     * the application (and its tests) can start without a key; it's needed on the first LLM call.
     *
     * <p>Keys that are not scoped to a workspace must name one on every request;
     * set ANTHROPIC_WORKSPACE_ID for those.
     */
    @Bean(destroyMethod = "close")
    @Lazy
    public AnthropicClient anthropicClient(@Value("${ANTHROPIC_API_KEY:}") String apiKey,
                                           @Value("${ANTHROPIC_WORKSPACE_ID:}") String workspaceId) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().fromEnv();
        if (!apiKey.isBlank()) {
            builder.apiKey(apiKey.strip());
        }
        if (!workspaceId.isBlank()) {
            builder.putHeader("anthropic-workspace-id", workspaceId.strip());
        }
        return builder.build();
    }

    /** LLM calls made at the same time (one per menu); kept low to stay within rate limits. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService llmExecutor(@Value("${agent.llm.parallel-calls:4}") int parallel) {
        return Executors.newFixedThreadPool(parallel);
    }
}
