package dev.proofstack.adapter.out.llm;

import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anthropic Messages API: {@code POST {baseUrl}/v1/messages}. The schema goes into the system prompt as an
 * instruction, which every Claude model follows, and the caller validates the answer, so there is no dependency on
 * a structured-output beta.
 */
final class AnthropicClient implements LlmClient {

    static final String VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 2048;

    private final HttpJson http;
    private final AiSettings settings;
    private final URI endpoint;

    AnthropicClient(HttpJson http, AiSettings settings) {
        this.http = http;
        this.settings = settings;
        this.endpoint = HttpJson.endpoint(settings.baseUrl(), "v1/messages");
    }

    @Override
    public String completeJson(String systemPrompt, String userPrompt, JsonNode schema) {
        ObjectNode body = http.mapper().createObjectNode();
        body.put("model", settings.model());
        body.put("max_tokens", MAX_TOKENS);
        body.put("temperature", settings.temperature());
        body.put("system", systemPrompt + "\n\nAnswer with one JSON object that matches this JSON Schema, and nothing else:\n"
                + schema);
        body.putArray("messages").addObject().put("role", "user").put("content", userPrompt);
        Map<String, String> headers = new LinkedHashMap<>(settings.requestHeaders());
        headers.put("anthropic-version", VERSION);
        JsonNode response;
        try {
            response = http.post(endpoint, headers, body, settings.providerHost(), settings.apiKey());
        } catch (HttpJson.HttpStatusException e) {
            throw ProviderErrors.of(e, settings.providerHost(), settings.model());
        }
        StringBuilder text = new StringBuilder();
        response.path("content").forEach(block -> {
            if ("text".equals(block.path("type").asString(""))) {
                text.append(block.path("text").asString(""));
            }
        });
        if (text.toString().isBlank()) {
            throw new ContentGenerationException(describe() + " returned an empty answer (stop_reason="
                    + response.path("stop_reason").asString("unknown") + ")", Kind.INVALID_OUTPUT);
        }
        return text.toString();
    }

    @Override
    public String describe() {
        return "anthropic/" + settings.model();
    }
}
