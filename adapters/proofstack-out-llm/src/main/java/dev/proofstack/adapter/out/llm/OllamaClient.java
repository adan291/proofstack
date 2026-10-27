package dev.proofstack.adapter.out.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;

import java.net.URI;
import java.util.Map;

/** Local models through Ollama's {@code /api/chat}, using its JSON-schema {@code format} option. */
final class OllamaClient implements LlmClient {

    static final URI DEFAULT_HOST = URI.create("http://localhost:11434");
    static final String DEFAULT_MODEL = "llama3.2";

    private final HttpJson http;
    private final URI host;
    private final String model;

    OllamaClient(HttpJson http, URI host, String model) {
        this.http = http;
        this.host = host;
        this.model = model;
    }

    @Override
    public String completeJson(String systemPrompt, String userPrompt, JsonNode schema) {
        ObjectNode body = http.mapper().createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        body.set("format", schema);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);
        body.putObject("options").put("temperature", 0.4);

        JsonNode response;
        try {
            response = http.post(host.resolve("/api/chat"), Map.of(), body, "Ollama", null);
        } catch (HttpJson.HttpStatusException e) {
            String hint = e.status() == 404 ? " (run: ollama pull " + model + ")" : "";
            throw new ContentGenerationException(e.getMessage() + hint, Kind.PROVIDER_UNAVAILABLE, e);
        } catch (ContentGenerationException e) {
            if (e.kind() == Kind.PROVIDER_UNAVAILABLE && e.getMessage().startsWith("cannot connect")) {
                throw new ContentGenerationException(e.getMessage() + ". Is Ollama running? (ollama serve)",
                        Kind.PROVIDER_UNAVAILABLE, e);
            }
            throw e;
        }

        String content = response.path("message").path("content").asString("");
        if (content.isBlank()) {
            throw new ContentGenerationException("Ollama returned an empty answer", Kind.INVALID_OUTPUT);
        }
        return content;
    }

    @Override
    public String describe() {
        return "ollama/" + model;
    }
}
