package dev.proofstack.adapter.out.llm;

import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiClientTest {

    private static final String KEY = "sk-test-secret-key-123456";
    private static final String ANSWER = "{\"text\":\"Hola\",\"changes\":[],\"questions\":[]}";

    private final ObjectMapper json = new ObjectMapper();
    private FakeLlmServer server;
    private JsonNode schema;

    @BeforeEach
    void start() throws IOException {
        server = new FakeLlmServer();
        schema = json.readTree("{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},"
                + "\"required\":[\"text\"],\"additionalProperties\":false}");
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private OpenAiClient client(String key, Map<String, String> headers, Duration timeout) {
        AiSettings settings = new AiSettings(Protocol.OPENAI, server.uri(), "gpt-test", key, "env", null, headers, 60,
                0.2, false);
        return new OpenAiClient(new HttpJson(HttpJson.defaultClient(), json, timeout), settings);
    }

    private OpenAiClient client() {
        return client(KEY, Map.of(), Duration.ofSeconds(5));
    }

    private static String reply(String content) throws IOException {
        return new ObjectMapper().writeValueAsString(Map.of("choices",
                java.util.List.of(Map.of("message", Map.of("role", "assistant", "content", content)))));
    }

    @Test
    void postsChatCompletionsWithBearerKeySchemaAndTemperature() throws IOException {
        server.responseBody = reply(ANSWER);
        assertThat(client().completeJson("SYSTEM", "USER", schema)).isEqualTo(ANSWER);

        assertThat(server.lastPath).isEqualTo("/chat/completions");
        assertThat(server.lastHeaders).containsEntry("authorization", "Bearer " + KEY);
        JsonNode body = json.readTree(server.lastBody);
        assertThat(body.path("model").asString()).isEqualTo("gpt-test");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(body.at("/messages/0/role").asString()).isEqualTo("system");
        assertThat(body.at("/messages/0/content").asString()).isEqualTo("SYSTEM");
        assertThat(body.at("/messages/1/content").asString()).isEqualTo("USER");
        assertThat(body.at("/response_format/type").asString()).isEqualTo("json_schema");
        assertThat(body.at("/response_format/json_schema/schema/required/0").asString()).isEqualTo("text");
    }

    @Test
    void usesTheConfiguredAuthHeaderAndExtraHeaders() throws IOException {
        server.responseBody = reply(ANSWER);
        AiSettings azure = new AiSettings(Protocol.OPENAI, server.uri(), "dep", KEY, "env", "api-key",
                Map.of("HTTP-Referer", "https://example.com"), 60, 0.4, false);
        new OpenAiClient(new HttpJson(HttpJson.defaultClient(), json, Duration.ofSeconds(5)), azure)
                .completeJson("s", "u", schema);
        assertThat(server.lastHeaders).containsEntry("api-key", KEY).containsEntry("http-referer", "https://example.com")
                .doesNotContainKey("authorization");
    }

    @Test
    void keepsTheQueryOfTheBaseUrlWhenAppendingThePath() throws IOException {
        server.responseBody = reply(ANSWER);
        AiSettings azure = new AiSettings(Protocol.OPENAI, URI.create(server.uri() + "/openai/deployments/d?api-version=2024-10-21"),
                "d", KEY, "env", "api-key", Map.of(), 60, 0.4, false);
        new OpenAiClient(new HttpJson(HttpJson.defaultClient(), json, Duration.ofSeconds(5)), azure)
                .completeJson("s", "u", schema);
        assertThat(server.lastPath).isEqualTo("/openai/deployments/d/chat/completions");
        assertThat(server.lastQuery).isEqualTo("api-version=2024-10-21");
    }

    @Test
    void localModelsNeedNoKeyAndSendNoAuthHeader() throws IOException {
        server.responseBody = reply(ANSWER);
        client(null, Map.of(), Duration.ofSeconds(5)).completeJson("s", "u", schema);
        assertThat(server.lastHeaders).doesNotContainKey("authorization");
    }

    @Test
    void fallsBackToJsonObjectAndThenToNoResponseFormatOnHttp400() throws IOException {
        server.enqueue(400, "{\"error\":\"response_format json_schema is not supported\"}");
        server.enqueue(400, "{\"error\":\"response_format json_object is not supported\"}");
        server.enqueue(200, reply(ANSWER));

        assertThat(client().completeJson("s", "u", schema)).isEqualTo(ANSWER);

        assertThat(server.bodies).hasSize(3);
        assertThat(json.readTree(server.bodies.get(0)).at("/response_format/type").asString()).isEqualTo("json_schema");
        assertThat(json.readTree(server.bodies.get(1)).at("/response_format/type").asString()).isEqualTo("json_object");
        assertThat(json.readTree(server.bodies.get(2)).has("response_format")).isFalse();
    }

    @Test
    void remembersTheFormatThatWorkedSoLaterCallsDoNotRetry() throws IOException {
        server.enqueue(400, "{}");
        server.enqueue(200, reply(ANSWER));
        OpenAiClient client = client();
        client.completeJson("s", "u", schema);
        server.enqueue(200, reply(ANSWER));
        client.completeJson("s", "u", schema);
        assertThat(server.bodies).hasSize(3);
        assertThat(json.readTree(server.bodies.get(2)).at("/response_format/type").asString()).isEqualTo("json_object");
    }

    @Test
    void failsClearlyWhenEvenTheLastAttemptReturns400() {
        server.status = 400;
        server.responseBody = "{\"error\":\"model 'gpt-test' does not exist\"}";
        assertThatThrownBy(() -> client().completeJson("s", "u", schema))
                .isInstanceOfSatisfying(ContentGenerationException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.PROVIDER_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("400", "does not exist");
                });
        assertThat(server.bodies).hasSize(3);
    }

    @Test
    void rejectedKeyGivesAnActionableMessageWithoutTheKeyOrTheBody() {
        server.status = 401;
        server.responseBody = "{\"error\":{\"message\":\"Incorrect API key provided: " + KEY + "\"}}";
        assertThatThrownBy(() -> client().completeJson("s", "u", schema))
                .isInstanceOfSatisfying(ContentGenerationException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.PROVIDER_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("401", "key was rejected").doesNotContain(KEY, "Incorrect");
                });
        assertThat(server.bodies).hasSize(1);
    }

    @Test
    void rateLimitAndServerErrorsAreReportedWithoutTheKey() {
        server.status = 429;
        server.responseBody = "{\"error\":\"slow down " + KEY + "\"}";
        assertThatThrownBy(() -> client().completeJson("s", "u", schema))
                .isInstanceOfSatisfying(ContentGenerationException.class, e ->
                        assertThat(e.getMessage()).contains("429", "rate limit").doesNotContain(KEY));
        server.status = 503;
        server.responseBody = "{\"error\":\"overloaded " + KEY + "\"}";
        assertThatThrownBy(() -> client().completeJson("s", "u", schema))
                .isInstanceOfSatisfying(ContentGenerationException.class, e ->
                        assertThat(e.getMessage()).contains("503", "provider").doesNotContain(KEY));
        assertThat(server.bodies).hasSize(2);
    }

    @Test
    void timeoutIsProviderUnavailable() {
        server.delayMillis = 1500;
        server.responseBody = "{}";
        assertThatThrownBy(() -> client(KEY, Map.of(), Duration.ofMillis(300)).completeJson("s", "u", schema))
                .isInstanceOfSatisfying(ContentGenerationException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.PROVIDER_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("did not answer").doesNotContain(KEY);
                });
    }

    @Test
    void emptyChoicesAreInvalidOutput() {
        server.responseBody = "{\"choices\":[]}";
        assertThatThrownBy(() -> client().completeJson("s", "u", schema))
                .isInstanceOfSatisfying(ContentGenerationException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.INVALID_OUTPUT));
    }

    @Test
    void answerSplitIntoContentPartsIsJoined() throws IOException {
        server.responseBody = json.writeValueAsString(Map.of("choices", java.util.List.of(Map.of("message",
                Map.of("content", java.util.List.of(Map.of("type", "text", "text", "{\"text\":"),
                        Map.of("type", "text", "text", "\"x\"}")))))));
        assertThat(client().completeJson("s", "u", schema)).isEqualTo("{\"text\":\"x\"}");
    }
}
