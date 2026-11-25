package dev.proofstack.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import dev.proofstack.core.application.AskService;

/**
 * The rate limit on a real Tomcat: MockMvc does not parse paths the way the servlet container does, so a spelling
 * of {@code /api/ask} that reaches the controller must not be able to skip the limit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "proofstack.profile=${proofstack.repoRoot:..}/profiles/example/profile.yaml",
        "proofstack.index-on-startup=false",
        "proofstack.requests-per-minute=2",
        "proofstack.rag.min-score=0.1",
        "management.server.port=0"
})
class RateLimitOnRealServerTest {

    @TestConfiguration
    static class FakeModels {
        @Bean
        @Primary
        EmbeddingModel fakeEmbeddingModel() {
            return new Fakes.BagOfWordsEmbeddingModel();
        }

        @Bean
        @Primary
        Fakes.ScriptedChatModel fakeChatModel() {
            return new Fakes.ScriptedChatModel(p -> "{\"answerable\":false,\"answer\":\"\",\"citations\":[]}");
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    AskService askService;

    private final HttpClient http = HttpClient.newHttpClient();

    /** Each path is asked from the same address: after the limit, every request that reaches the controller is 429. */
    @ParameterizedTest
    @ValueSource(strings = {"/api/ask;x=1", "/api/ask;jsessionid=1", "/api//ask", "/api/./ask", "/api/x/../ask"})
    void noSpellingOfThePathSkipsTheLimit(String path) throws Exception {
        askService.index();
        // Different tests share the server and the client address: use the plain path until it is limited.
        for (int i = 0; i < 3; i++) {
            ask("/api/ask");
        }

        int status = ask(path);

        assertThat(status).as(path).isNotEqualTo(200);
    }

    private int ask(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"Kafka?\"}"))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
