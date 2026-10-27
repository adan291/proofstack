package dev.proofstack.service;

import dev.proofstack.adapter.out.springai.VectorStoreKnowledgeIndex;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metrics and health that describe the assistant in business terms (ADR-009). */
@Configuration(proxyBeanMethods = false)
class ObservabilityConfiguration {

    static final String RATE_LIMITED = "proofstack.ask.rate_limited";
    static final String CHUNKS = "proofstack.knowledge.chunks";

    @Bean
    Gauge knowledgeChunksGauge(MeterRegistry registry, VectorStoreKnowledgeIndex index) {
        return Gauge.builder(CHUNKS, index, VectorStoreKnowledgeIndex::size)
                .description("Profile chunks currently in the semantic index")
                .register(registry);
    }

    @Bean
    Counter rateLimitedCounter(MeterRegistry registry) {
        return Counter.builder(RATE_LIMITED)
                .description("Questions rejected by the per-client rate limit")
                .register(registry);
    }

    /** DOWN while the index is empty: the service cannot answer anything useful yet. */
    @Bean
    HealthIndicator knowledgeIndexHealthIndicator(VectorStoreKnowledgeIndex index) {
        return () -> index.size() > 0
                ? Health.up().withDetail("chunks", index.size()).build()
                : Health.down().withDetail("chunks", 0).withDetail("reason", "knowledge index is empty").build();
    }
}
