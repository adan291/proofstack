package dev.proofstack.adapter.out.llm;

import java.util.Arrays;
import java.util.Locale;

/**
 * The four wire protocols the assistant speaks (ADR-017). Almost every provider, cloud or local, speaks one of them,
 * so a provider is configuration and not code.
 */
public enum Protocol {
    /** {@code POST {baseUrl}/chat/completions}: OpenAI, Azure OpenAI, Mistral, Groq, OpenRouter, LM Studio, vLLM... */
    OPENAI("Authorization"),
    /** {@code POST {baseUrl}/v1/messages}. */
    ANTHROPIC("x-api-key"),
    /** Gemini {@code generateContent}. */
    GEMINI("x-goog-api-key"),
    /** Ollama {@code /api/chat}. */
    OLLAMA("Authorization");

    private final String defaultAuthHeader;

    Protocol(String defaultAuthHeader) {
        this.defaultAuthHeader = defaultAuthHeader;
    }

    public String defaultAuthHeader() {
        return defaultAuthHeader;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Protocol parse(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("unknown protocol '" + value + "'. Use one of: "
                    + String.join(", ", Arrays.stream(values()).map(Protocol::id).toList()));
        }
    }
}
