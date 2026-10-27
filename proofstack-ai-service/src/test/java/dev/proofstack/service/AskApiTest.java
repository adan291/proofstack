package dev.proofstack.service;

import dev.proofstack.core.application.AskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full application context with fake models: wiring, HTTP contract, CORS and rate limiting. */
@SpringBootTest(properties = {
        "proofstack.profile=${proofstack.repoRoot:..}/profiles/example/profile.yaml",
        "proofstack.index-on-startup=false",
        "proofstack.requests-per-minute=3",
        "proofstack.rag.min-score=0.1",
        "proofstack.cors.allowed-origins=https://jane.example.dev"
})
@AutoConfigureMockMvc
class AskApiTest {

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
            return new Fakes.ScriptedChatModel(p -> "{}");
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    AskService askService;

    @Autowired
    Fakes.ScriptedChatModel chat;

    @Autowired
    ChatModel chatModel;

    private static int clientCounter;

    @BeforeEach
    void index() {
        askService.index();
    }

    /** Each test uses its own client address so the rate limiter does not leak between tests. */
    private static String nextClient() {
        return "10.0.0." + (++clientCounter);
    }

    private org.springframework.test.web.servlet.ResultActions ask(String client, String body) throws Exception {
        return mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(r -> {
                    r.setRemoteAddr(client);
                    return r;
                }));
    }

    @Test
    void fakeChatModelIsTheOneWired() {
        org.assertj.core.api.Assertions.assertThat(chatModel).isSameAs(chat);
    }

    @Test
    void answersWithCitations() throws Exception {
        chat.script(p -> "{\"answerable\":true,\"answer\":\"Jane extracted billing using Kafka events.\","
                + "\"citations\":[\"project:billing-events\"]}");

        ask(nextClient(), "{\"question\":\"Has Jane used Kafka for billing events?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.citations[0].id").value("project:billing-events"))
                .andExpect(jsonPath("$.citations[0].type").value("project"));
    }

    @Test
    void unrelatedQuestionIsUnknown() throws Exception {
        chat.script(p -> "{\"answerable\":false,\"answer\":\"\",\"citations\":[]}");

        ask(nextClient(), "{\"question\":\"Does she like paella?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.answer").value(""));
    }

    @Test
    void invalidQuestionsAre400() throws Exception {
        String client = nextClient();
        ask(client, "{\"question\":\"\"}").andExpect(status().isBadRequest());
        ask(client, "not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("question")));
    }

    @Test
    void modelFailureIs503WithoutInternals() throws Exception {
        chat.script(p -> {
            throw new IllegalStateException("http://internal-host:11434 refused");
        });

        ask(nextClient(), "{\"question\":\"Kafka billing events?\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("the assistant is temporarily unavailable"))
                .andExpect(content().string(not(containsString("internal-host"))));
    }

    @Test
    void rateLimitReturns429WithRetryAfter() throws Exception {
        chat.script(p -> "{\"answerable\":false,\"answer\":\"\",\"citations\":[]}");
        String client = nextClient();
        for (int i = 0; i < 3; i++) {
            ask(client, "{\"question\":\"Kafka?\"}").andExpect(status().isOk());
        }
        ask(client, "{\"question\":\"Kafka?\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // Another client is not affected.
        ask(nextClient(), "{\"question\":\"Kafka?\"}").andExpect(status().isOk());
    }

    @Test
    void corsAllowsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/ask").header("Origin", "https://jane.example.dev")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://jane.example.dev"));

        mvc.perform(options("/api/ask").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusReportsIndexSize() throws Exception {
        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexedChunks").value(org.hamcrest.Matchers.greaterThan(3)));
    }

    private static org.springframework.test.web.servlet.result.ContentResultMatchers content() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content();
    }
}
