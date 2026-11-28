package dev.proofstack.adapter.out.llm;

import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;

/**
 * The de-facto standard: {@code POST {baseUrl}/chat/completions}. Spoken by OpenAI, Azure OpenAI, Mistral, Groq,
 * OpenRouter, DeepSeek, Together, LM Studio, llama.cpp server, vLLM, LocalAI and Ollama under {@code /v1}.
 *
 * <p>Structured output is asked for with {@code json_schema}. A provider that answers 400 to it gets
 * {@code json_object}, and then no {@code response_format} at all; the caller validates the JSON in every case.
 * The format that worked is remembered, so only the first call of a session can cost extra requests.
 */
final class OpenAiClient implements LlmClient {

    private static final int FORMATS = 3;

    private final HttpJson http;
    private final AiSettings settings;
    private final URI endpoint;
    /** 0 = json_schema, 1 = json_object, 2 = none. */
    private volatile int format;

    OpenAiClient(HttpJson http, AiSettings settings) {
        this.http = http;
        this.settings = settings;
        this.endpoint = HttpJson.endpoint(settings.baseUrl(), "chat/completions");
    }

    @Override
    public String completeJson(String systemPrompt, String userPrompt, JsonNode schema) {
        HttpJson.HttpStatusException last = null;
        for (int attempt = format; attempt < FORMATS; attempt++) {
            try {
                JsonNode response = http.post(endpoint, settings.requestHeaders(),
                        body(systemPrompt, userPrompt, schema, attempt), settings.providerHost(), settings.apiKey());
                format = attempt;
                return extractText(response);
            } catch (HttpJson.HttpStatusException e) {
                if (e.status() != 400) {
                    throw ProviderErrors.of(e, settings.providerHost(), settings.model());
                }
                last = e;
            }
        }
        throw ProviderErrors.of(last, settings.providerHost(), settings.model());
    }

    private ObjectNode body(String system, String user, JsonNode schema, int attempt) {
        ObjectNode body = http.mapper().createObjectNode();
        body.put("model", settings.model());
        body.put("temperature", settings.temperature());
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        if (attempt == 0) {
            ObjectNode format = body.putObject("response_format");
            format.put("type", "json_schema");
            ObjectNode spec = format.putObject("json_schema");
            spec.put("name", "answer");
            spec.put("strict", true);
            spec.set("schema", schema);
        } else if (attempt == 1) {
            body.putObject("response_format").put("type", "json_object");
        }
        return body;
    }

    private String extractText(JsonNode response) {
        JsonNode message = response.path("choices").path(0).path("message");
        if (message.isMissingNode()) {
            throw new ContentGenerationException(describe() + " returned no choices", Kind.INVALID_OUTPUT);
        }
        JsonNode content = message.path("content");
        StringBuilder text = new StringBuilder();
        if (content.isArray()) {
            content.forEach(part -> text.append(part.path("text").asString("")));
        } else {
            text.append(content.asString(""));
        }
        if (text.toString().isBlank()) {
            throw new ContentGenerationException(describe() + " returned an empty answer (finish_reason="
                    + response.path("choices").path(0).path("finish_reason").asString("unknown") + ")", Kind.INVALID_OUTPUT);
        }
        return text.toString();
    }

    @Override
    public String describe() {
        return "openai/" + settings.model();
    }
}
