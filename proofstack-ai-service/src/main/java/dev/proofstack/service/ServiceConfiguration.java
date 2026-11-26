package dev.proofstack.service;

import dev.proofstack.adapter.in.rest.ApiExceptionHandler;
import dev.proofstack.adapter.in.rest.AskController;
import dev.proofstack.adapter.in.rest.RateLimitFilter;
import dev.proofstack.adapter.yaml.YamlProfileSource;
import dev.proofstack.adapter.out.springai.ChatModelAnswerGenerator;
import dev.proofstack.adapter.out.springai.VectorStoreKnowledgeIndex;
import dev.proofstack.core.application.AskService;
import dev.proofstack.core.domain.Profile;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.util.Objects;

/** Composition root: the only place that knows both the core and the concrete adapters. */
@Configuration(proxyBeanMethods = false)
class ServiceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ServiceConfiguration.class);

    @Bean
    Profile profile(ProofstackProperties props) {
        Objects.requireNonNull(props.profile(), "proofstack.profile (PROOFSTACK_PROFILE) must point to a profile.yaml");
        // An invalid profile throws here and the service refuses to start (R3.7).
        return new YamlProfileSource().load(props.profile());
    }

    @Bean
    VectorStoreKnowledgeIndex knowledgeIndex(EmbeddingModel embeddingModel, ProofstackProperties props) {
        return new VectorStoreKnowledgeIndex(SimpleVectorStore.builder(embeddingModel).build(),
                props.rag().documentPrefix(), props.rag().queryPrefix());
    }

    @Bean
    AskService askService(Profile profile, VectorStoreKnowledgeIndex index, ChatModel chatModel,
                          ProofstackProperties props) {
        return new AskService(profile, index, new ChatModelAnswerGenerator(chatModel),
                new AskService.RetrievalPolicy(props.rag().topK(), props.rag().minScore()));
    }

    @Bean
    ApplicationRunner indexOnStartup(AskService askService, ProofstackProperties props) {
        return args -> {
            if (props.indexOnStartup()) {
                log.info("Indexed {} knowledge chunks", askService.index());
            }
        };
    }

    @Bean
    AskController askController(AskService askService, VectorStoreKnowledgeIndex index, MeterRegistry registry,
                                @Value("${spring.ai.ollama.chat.model:unknown}") String model) {
        return new AskController(new ObservedAskPortfolio(askService, registry),
                new AskController.ServiceInfo(index::size, model));
    }

    @Bean
    ApiExceptionHandler apiExceptionHandler() {
        return new ApiExceptionHandler();
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(ProofstackProperties props, Counter rateLimitedCounter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
                new RateLimitFilter(new RateLimitFilter.Limits(props.requestsPerMinute(),
                        props.globalRequestsPerMinute(), props.maxConcurrentQuestions()),
                        Clock.systemUTC(), rateLimitedCounter::increment));
        registration.addUrlPatterns("/api/ask");
        return registration;
    }

    @Bean
    WebMvcConfigurer cors(ProofstackProperties props) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                if (props.cors().allowedOrigins().isEmpty()) {
                    return; // same-origin only
                }
                registry.addMapping("/api/**")
                        .allowedOrigins(props.cors().allowedOrigins().toArray(String[]::new))
                        .allowedMethods("GET", "POST")
                        .allowedHeaders("Content-Type")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }
}
