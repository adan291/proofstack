package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.knowledge.DraftAnswer;
import dev.proofstack.core.domain.knowledge.KnowledgeChunk;

import java.util.List;

/** Driven port: answers a question using only the given context chunks. */
public interface AnswerGenerator {

    /**
     * @throws dev.proofstack.core.domain.content.ContentGenerationException if the model is unavailable or answers garbage
     */
    DraftAnswer answer(String question, String language, List<KnowledgeChunk> context);
}
