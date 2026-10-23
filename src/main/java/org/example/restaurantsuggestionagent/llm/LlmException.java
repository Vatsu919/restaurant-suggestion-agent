package org.example.restaurantsuggestionagent.llm;

/** The LLM returned no usable answer (refusal, truncated output, empty content). */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }
}
