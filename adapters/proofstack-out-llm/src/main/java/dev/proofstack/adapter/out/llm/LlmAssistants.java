package dev.proofstack.adapter.out.llm;

import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.port.out.TextDrafter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/** Factory used by the composition root: from the resolved settings to a working drafter or connection test. */
public final class LlmAssistants {

    /** Outcome of {@link #test}; {@code error} is safe to show and never contains the key. */
    public record ConnectionResult(boolean ok, String model, long millis, String error) {
    }

    private LlmAssistants() {
    }

    public static TextDrafter drafter(AiSettings settings) {
        ObjectMapper json = new ObjectMapper();
        return new LlmTextDrafter(client(settings, json), new AssistantPrompts(), json);
    }

    /** One tiny request: proves the URL, the key and the model without sending any profile data. */
    public static ConnectionResult test(AiSettings settings) {
        ObjectMapper json = new ObjectMapper();
        long start = System.nanoTime();
        try {
            LlmClient client = client(settings, json);
            JsonNode schema = json.readTree("{\"type\":\"object\",\"additionalProperties\":false,"
                    + "\"required\":[\"ok\"],\"properties\":{\"ok\":{\"type\":\"boolean\"}}}");
            client.completeJson("Answer with the JSON object {\"ok\": true} and nothing else.", "ping", schema);
            return new ConnectionResult(true, settings.model(), millisSince(start), "");
        } catch (ContentGenerationException e) {
            return new ConnectionResult(false, settings.model(), millisSince(start), e.getMessage());
        }
    }

    static LlmClient client(AiSettings settings, ObjectMapper json) {
        HttpJson http = new HttpJson(HttpJson.defaultClient(), json, Duration.ofSeconds(settings.timeoutSeconds()));
        return switch (settings.protocol()) {
            case OPENAI -> new OpenAiClient(http, settings);
            case ANTHROPIC -> new AnthropicClient(http, settings);
            case GEMINI -> new GeminiClient(http, settings);
            case OLLAMA -> new OllamaClient(http, settings);
        };
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
