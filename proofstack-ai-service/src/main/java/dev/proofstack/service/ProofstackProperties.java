package dev.proofstack.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.util.List;

/** Typed configuration under {@code proofstack.*}. */
@ConfigurationProperties("proofstack")
public record ProofstackProperties(
        Path profile,
        @DefaultValue("true") boolean indexOnStartup,
        @DefaultValue Rag rag,
        @DefaultValue Cors cors,
        @DefaultValue("10") int requestsPerMinute) {

    public record Rag(
            @DefaultValue("4") int topK,
            @DefaultValue("0.5") double minScore,
            @DefaultValue("") String documentPrefix,
            @DefaultValue("") String queryPrefix) {
    }

    public record Cors(@DefaultValue List<String> allowedOrigins) {
    }
}
