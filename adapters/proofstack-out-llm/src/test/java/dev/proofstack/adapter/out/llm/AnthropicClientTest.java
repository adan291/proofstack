package dev.proofstack.adapter.out.llm;

import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicClientTest {

    private static final String KEY = "sk-ant-test-secret-key-123456";
    private static final String ANSWER = "{\"text\":\"Hola\",\"changes\":[],\"questions\":[]}";

    private final ObjectMapper json = new ObjectMapper();
    private FakeLlmServer server;
    private JsonNode schema;

    @BeforeEach
    void start() throws IOException {
        server = new FakeLlmServer();
        schema = json.readTree("{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}");
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private AnthropicClient client(Duration timeout) {
        AiSettings settings = new AiSettings(Protocol.ANTHROPIC, server.uri(), "claude-test", KEY, "env", null,
                Map.of("X-Extra", "1"), 60, 0.3, false);
        return new AnthropicClient(new HttpJson(HttpJson.defaultClient(), json, timeout), settings);
    }

    private static String reply(String... texts) throws IOException {
        return new ObjectMapper().writeValueAsString(Map.of("content",
                java.util.Arrays.stream(texts).map(t -> Map.of("type", "text", "text", t)).toList()));
    }

    @Test
    void postsMessagesWithKeyVersionSystemAndSchemaInstruction() throws IOException {
        server.responseBody = reply(ANSWER);
        assertThat(client(Duration.ofSeconds(5)).completeJson("SYSTEM", "USER", schema)).isEqualTo(ANSWER);

        assertThat(server.lastPath).isEqualTo("/v1/messages");
        assertThat(server.lastHeaders).containsEntry("x-api-key", KEY).containsEntry("anthropic-version", "2023-06-01")
                .containsEntry("x-extra", "1").doesNotContainKey("authorization");
        JsonNode body = json.readTree(server.lastBody);
        assertThat(body.path("model").asString()).isEqualTo("claude-test");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.3);
        assertThat(body.path("max_tokens").asInt()).isPositive();
        assertThat(body.path("system").asString()).startsWith("SYSTEM").contains("JSON").contains("\"required\"");
        assertThat(body.at("/messages/0/role").asString()).isEqualTo("user");
        assertThat(body.at("/messages/0/content").asString()).isEqualTo("USER");
    }

    @Test
    void joinsTextBlocksAndIgnoresOthers() throws IOException {
        server.responseBody = json.writeValueAsString(Map.of("content",
                List.of(Map.of("type", "thinking", "thinking", "..."), Map.of("type", "text", "text", "{\"text\":"),
                        Map.of("type", "text", "text", "\"x\"}"))));
        assertThat(client(Duration.ofSeconds(5)).completeJson("s", "u", schema)).isEqualTo("{\"text\":\"x\"}");
    }

    @Test
    void emptyContentIsInvalidOutput() {
        server.responseBody = "{\"content\":[],\"stop_reason\":\"max_tokens\"}";
        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).completeJson("s", "u", schema))
                .isInstanceOfSatisfying(ContentGenerationException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.INVALID_OUTPUT);
                    assertThat(e.getMessage()).contains("max_tokens");
                });
    }

    @Test
    void errorsAreActionableAndNeverContainTheKey() {
        for (int status : new int[] {401, 429, 500, 529}) {
            server.status = status;
            server.responseBody = "{\"error\":{\"message\":\"bad " + KEY + "\"}}";
            assertThatThrownBy(() -> client(Duration.ofSeconds(5)).completeJson("s", "u", schema))
                    .isInstanceOfSatisfying(ContentGenerationException.class, e -> {
                        assertThat(e.kind()).isEqualTo(Kind.PROVIDER_UNAVAILABLE);
                        assertThat(e.getMessage()).contains(String.valueOf(status)).doesNotContain(KEY);
                    });
        }
        assertThat(server.bodies).hasSize(4);
    }

    @Test
    void timeoutIsProviderUnavailable() {
        server.delayMillis = 1500;
        server.responseBody = "{}";
        assertThatThrownBy(() -> client(Duration.ofMillis(300)).completeJson("s", "u", schema))
                .isInstanceOfSatisfying(ContentGenerationException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.PROVIDER_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("did not answer");
                });
    }
}
