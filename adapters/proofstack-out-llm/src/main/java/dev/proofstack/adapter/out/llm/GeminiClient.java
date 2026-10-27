package dev.proofstack.adapter.out.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Gemini API ({@code generateContent}) with JSON-schema constrained output.
 * The API key travels in the {@code x-goog-api-key} header, never in the URL.
 */
final class GeminiClient implements LlmClient {

    static final URI DEFAULT_BASE_URL = URI.create("https://generativelanguage.googleapis.com");
    static final String DEFAULT_MODEL = "gemini-3.8-flash";

    private final HttpJson http;
    private final URI baseUrl;
    private final String apiKey;
    private final String model;

    GeminiClient(HttpJson http, URI baseUrl, String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ContentGenerationException(
                    "GEMINI_API_KEY is not set. Create a key in Google AI Studio and export it as an environment variable",
                    Kind.PROVIDER_UNAVAILABLE);
        }
        this.http = http;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String completeJson(String systemPrompt, String userPrompt, JsonNode schema) {
        ObjectNode body = http.mapper().createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", systemPrompt);
        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", userPrompt);
        ObjectNode config = body.putObject("generationConfig");
        config.put("temperature", 0.4);
        ObjectNode text = config.putObject("responseFormat").putObject("text");
        text.put("mimeType", "application/json");
        text.set("schema", schema);

        URI uri = baseUrl.resolve("/v1beta/models/" + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent");
        JsonNode response;
        try {
            response = http.post(uri, Map.of("x-goog-api-key", apiKey), body, "Gemini", apiKey);
        } catch (HttpJson.HttpStatusException e) {
            String hint = switch (e.status()) {
                case 400 -> " (check the model name and request)";
                case 401, 403 -> " (the API key was rejected)";
                case 404 -> " (model '" + model + "' not found)";
                case 429 -> " (quota or rate limit exceeded, try later)";
                default -> "";
            };
            throw new ContentGenerationException(e.getMessage() + hint, Kind.PROVIDER_UNAVAILABLE, e);
        }
        return extractText(response);
    }

    private static String extractText(JsonNode response) {
        JsonNode candidate = response.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            String reason = response.path("promptFeedback").path("blockReason").asString("no candidates");
            throw new ContentGenerationException("Gemini returned no content: " + reason, Kind.INVALID_OUTPUT);
        }
        StringBuilder text = new StringBuilder();
        candidate.path("content").path("parts").forEach(part -> text.append(part.path("text").asString("")));
        if (text.isEmpty()) {
            throw new ContentGenerationException("Gemini returned an empty answer (finishReason="
                    + candidate.path("finishReason").asString("unknown") + ")", Kind.INVALID_OUTPUT);
        }
        return text.toString();
    }

    @Override
    public String describe() {
        return "gemini/" + model;
    }
}
