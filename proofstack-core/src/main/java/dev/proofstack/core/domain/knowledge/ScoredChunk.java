package dev.proofstack.core.domain.knowledge;

import java.util.Objects;

/** A chunk returned by a similarity search; higher score means more similar (0..1 for cosine). */
public record ScoredChunk(KnowledgeChunk chunk, double score) {

    public ScoredChunk {
        Objects.requireNonNull(chunk, "chunk");
    }
}
