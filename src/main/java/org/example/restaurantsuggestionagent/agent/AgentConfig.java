package org.example.restaurantsuggestionagent.agent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    /** Threads that run whole pipelines; each run occupies one thread from start to finish. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService agentExecutor(@Value("${agent.max-concurrent-runs:4}") int maxConcurrentRuns) {
        return Executors.newFixedThreadPool(maxConcurrentRuns);
    }
}
