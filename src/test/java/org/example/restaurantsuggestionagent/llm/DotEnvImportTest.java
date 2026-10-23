package org.example.restaurantsuggestionagent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/** Checks that KEY=value lines in a .env file reach Spring's environment, as application.properties sets up. */
class DotEnvImportTest {

    @Configuration
    static class Empty {
    }

    @Test
    void keysInDotEnvAreReadAsProperties(@TempDir Path dir) throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, "GOOGLE_MAPS_KEY=fake-google-key\nANTHROPIC_API_KEY=fake-claude-key\n");

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .run("--spring.config.import=optional:file:" + env.toString().replace('\\', '/') + "[.properties]")) {
            assertThat(ctx.getEnvironment().getProperty("GOOGLE_MAPS_KEY")).isEqualTo("fake-google-key");
            assertThat(ctx.getEnvironment().getProperty("ANTHROPIC_API_KEY")).isEqualTo("fake-claude-key");
        }
    }
}
