package dev.proofstack.service;

import dev.proofstack.adapter.out.springai.VectorStoreKnowledgeIndex;
import dev.proofstack.core.application.AskService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "proofstack.profile=${proofstack.repoRoot:..}/profiles/example/profile.yaml",
        "proofstack.index-on-startup=false",
        "proofstack.requests-per-minute=1",
        "proofstack.rag.min-score=0.1"
})
@AutoConfigureMockMvc
class ObservabilityTest {

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
            return new Fakes.ScriptedChatModel(p ->
                    "{\"answerable\":true,\"answer\":\"Jane used Kafka.\",\"citations\":[\"project:billing-events\"]}");
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MeterRegistry registry;

    @Autowired
    AskService askService;

    @Autowired
    VectorStoreKnowledgeIndex index;

    @Autowired
    List<HealthIndicator> indicators;

    @Autowired
    HealthIndicator knowledgeIndexHealthIndicator;

    @Test
    void recordsDomainMetricsWithoutQuestionText() throws Exception {
        askService.index();
        String question = "{\"question\":\"Kafka billing events?\"}";
        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content(question)
                .with(r -> { r.setRemoteAddr("10.9.9.1"); return r; }));
        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content(question)
                .with(r -> { r.setRemoteAddr("10.9.9.1"); return r; }));

        assertThat(registry.get(ObservedAskPortfolio.TIMER).tag("status", "answered").timer().count()).isEqualTo(1);
        assertThat(registry.get(ObservabilityConfiguration.RATE_LIMITED).counter().count()).isEqualTo(1);
        assertThat(registry.get(ObservabilityConfiguration.CHUNKS).gauge().value()).isEqualTo(index.size());
        assertThat(registry.get(ObservedAskPortfolio.QUESTION_LENGTH).summary().totalAmount())
                .isEqualTo("Kafka billing events?".length());
        // No meter name or tag ever carries the question itself.
        assertThat(registry.getMeters()).allSatisfy(m ->
                assertThat(m.getId().toString()).doesNotContainIgnoringCase("billing events?"));
    }

    @Test
    void healthIsDownUntilTheIndexHasChunks() {
        index.index(List.of());
        assertThat(knowledgeIndexHealthIndicator.health(false).getStatus()).isEqualTo(Status.DOWN);

        askService.index();
        assertThat(knowledgeIndexHealthIndicator.health(false).getStatus()).isEqualTo(Status.UP);
        assertThat(indicators).contains(knowledgeIndexHealthIndicator);
    }
}
