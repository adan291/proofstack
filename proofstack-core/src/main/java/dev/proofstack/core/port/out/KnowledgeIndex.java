package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.knowledge.KnowledgeChunk;
import dev.proofstack.core.domain.knowledge.ScoredChunk;

import java.util.List;

/** Driven port: semantic index over profile chunks (vector store today, anything tomorrow). */
public interface KnowledgeIndex {

    /** Replaces the index content. */
    void index(List<KnowledgeChunk> chunks);

    /** @return up to {@code topK} chunks, most similar first */
    List<ScoredChunk> search(String query, int topK);
}
