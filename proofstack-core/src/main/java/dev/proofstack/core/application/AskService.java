package dev.proofstack.core.application;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.content.GroundingCheck;
import dev.proofstack.core.domain.knowledge.Answer;
import dev.proofstack.core.domain.knowledge.DraftAnswer;
import dev.proofstack.core.domain.knowledge.InvalidQuestionException;
import dev.proofstack.core.domain.knowledge.KnowledgeChunk;
import dev.proofstack.core.domain.knowledge.ProfileKnowledge;
import dev.proofstack.core.domain.knowledge.ScoredChunk;
import dev.proofstack.core.port.in.AskPortfolioUseCase;
import dev.proofstack.core.port.in.IndexKnowledgeUseCase;
import dev.proofstack.core.port.out.AnswerGenerator;
import dev.proofstack.core.port.out.KnowledgeIndex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Retrieval-augmented answering with guard rails (ADR-006, ADR-007):
 * nothing relevant → UNKNOWN without calling the model; citations must point at retrieved chunks;
 * an answer without valid citations is UNKNOWN; unsupported numbers become warnings.
 */
public final class AskService implements AskPortfolioUseCase, IndexKnowledgeUseCase {

    public static final int MIN_QUESTION = 3;
    public static final int MAX_QUESTION = 500;

    /** @param minScore minimum similarity for a chunk to be considered relevant */
    public record RetrievalPolicy(int topK, double minScore) {
        public RetrievalPolicy {
            if (topK < 1 || topK > 20) {
                throw new IllegalArgumentException("topK must be between 1 and 20");
            }
            if (minScore < 0 || minScore > 1) {
                throw new IllegalArgumentException("minScore must be between 0 and 1");
            }
        }

        public static RetrievalPolicy defaults() {
            return new RetrievalPolicy(4, 0.5);
        }
    }

    private final Profile profile;
    private final KnowledgeIndex index;
    private final AnswerGenerator generator;
    private final RetrievalPolicy policy;

    public AskService(Profile profile, KnowledgeIndex index, AnswerGenerator generator, RetrievalPolicy policy) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.index = Objects.requireNonNull(index, "index");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public int index() {
        List<KnowledgeChunk> chunks = ProfileKnowledge.chunks(profile);
        index.index(chunks);
        return chunks.size();
    }

    @Override
    public Answer ask(String question) {
        String q = question == null ? "" : question.strip();
        if (q.length() < MIN_QUESTION || q.length() > MAX_QUESTION) {
            throw new InvalidQuestionException("question must have between " + MIN_QUESTION + " and "
                    + MAX_QUESTION + " characters");
        }

        Map<String, KnowledgeChunk> retrieved = new LinkedHashMap<>();
        for (ScoredChunk scored : index.search(q, policy.topK())) {
            if (scored.score() >= policy.minScore()) {
                retrieved.putIfAbsent(scored.chunk().id(), scored.chunk());
            }
        }
        if (retrieved.isEmpty()) {
            return Answer.unknown(List.of());
        }

        DraftAnswer draft = generator.answer(q, profile.language(), List.copyOf(retrieved.values()));

        List<String> warnings = new ArrayList<>();
        List<KnowledgeChunk> citations = new ArrayList<>();
        for (String id : draft.citedIds()) {
            KnowledgeChunk chunk = retrieved.get(id);
            if (chunk == null) {
                warnings.add("dropped citation to unknown source '" + id + "'");
            } else {
                citations.add(chunk);
            }
        }
        if (!draft.answerable() || draft.text().isEmpty() || citations.isEmpty()) {
            return Answer.unknown(warnings);
        }

        String sources = retrieved.values().stream().map(c -> c.title() + "\n" + c.text())
                .collect(Collectors.joining("\n"));
        for (String number : GroundingCheck.findUnsupportedNumbers(draft.text(), sources)) {
            warnings.add("'" + number + "' is not in the cited sources");
        }
        return new Answer(Answer.Status.ANSWERED, draft.text(), citations, warnings);
    }
}
