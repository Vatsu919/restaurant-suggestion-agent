package org.example.restaurantsuggestionagent.query;

import java.time.LocalDate;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;

import org.example.restaurantsuggestionagent.llm.LlmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Uses Claude structured outputs: the SDK derives a JSON schema from {@link TripQuery},
 * the API guarantees the reply matches it, and the SDK hands back a typed record.
 */
@Component
public class ClaudeQueryParser implements QueryParser {

    static final String SYSTEM_PROMPT = """
            You turn a traveller's restaurant request into structured fields.
            Rules:
            - Only record what the user actually said. Never invent a city, meal, budget or date.
            - diet: VEGETARIAN only if the user wants vegetarian or vegan food; NON_VEGETARIAN if they ask \
            for meat, fish or seafood; otherwise ANY.
            - strictness: VEGAN if they say vegan; NO_EGG if they say they don't eat egg; LACTO_OVO if they \
            are vegetarian and say nothing more; UNSPECIFIED when diet is not VEGETARIAN.
            - meals: words like "brunch" or "morning" mean BREAKFAST; "supper" or "evening" mean DINNER.
            - visitDate: resolve relative dates ("tomorrow", "next Friday") using the date given in the message.
            - userLanguage: the language the request is written in.
            - maxSpendPerPerson: only an amount the user states; 0 otherwise. Also set budget from it.
            - tastePreferences: how the user likes their food ("less spicy" → "mild, not spicy"); a mild \
            wish is a preference, not something to avoid.
            - priorities: only what the user says matters most ("mainly", "only", "most important", \
            "I really want"); empty when they just list wishes.
            """;

    private final AnthropicClient client;
    private final String model;

    public ClaudeQueryParser(@Lazy AnthropicClient client,
                             @Value("${agent.llm.model:claude-haiku-5-5}") String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public TripQuery parse(String userMessage, LocalDate today) {
        StructuredMessage<TripQuery> response = client.messages().create(buildRequest(userMessage, today));

        StopReason stopReason = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stopReason)) {
            throw new LlmException("The model declined to process this request");
        }
        if (StopReason.MAX_TOKENS.equals(stopReason)) {
            throw new LlmException("The model's answer was cut off before it finished");
        }
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
                .findFirst()
                .orElseThrow(() -> new LlmException("The model returned no structured answer"));
    }

    StructuredMessageCreateParams<TripQuery> buildRequest(String userMessage, LocalDate today) {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(4000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(TripQuery.class)
                .addUserMessage("Today's date: " + today + "\n\nRequest:\n" + userMessage)
                .build();
    }
}
