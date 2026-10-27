package dev.proofstack.core.application;

import dev.proofstack.core.application.AskService.RetrievalPolicy;
import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.EvidenceCategory;
import dev.proofstack.core.domain.Fixtures;
import dev.proofstack.core.domain.Lab;
import dev.proofstack.core.domain.LabType;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Skill;
import dev.proofstack.core.domain.knowledge.Answer;
import dev.proofstack.core.domain.knowledge.DraftAnswer;
import dev.proofstack.core.domain.knowledge.InvalidQuestionException;
import dev.proofstack.core.domain.knowledge.KnowledgeChunk;
import dev.proofstack.core.domain.knowledge.ProfileKnowledge;
import dev.proofstack.core.domain.knowledge.ScoredChunk;
import dev.proofstack.core.port.out.AnswerGenerator;
import dev.proofstack.core.port.out.KnowledgeIndex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AskServiceTest {

    private final Profile profile = new Profile("es",
            new PersonalInfo("Ada Lovelace", "Backend Engineer", "Builds systems", "London", "ada@example.com", List.of()),
            List.of(Fixtures.experience("acme", List.of("team"))), List.of(),
            List.of(Fixtures.project("kafka", Area.MESSAGING)),
            List.of(new Skill("Kafka", Area.MESSAGING), new Skill("Java", Area.BACKEND)),
            List.of(Fixtures.evidence("team", EvidenceCategory.LEADERSHIP)),
            List.of(new Lab("ai", "AI Lab", LabType.AI, "Experiments", List.of("kafka"))));

    /** Index whose search returns every indexed chunk with a fixed score. */
    private static final class FakeIndex implements KnowledgeIndex {
        final List<KnowledgeChunk> chunks = new ArrayList<>();
        double score = 0.9;

        @Override
        public void index(List<KnowledgeChunk> c) {
            chunks.clear();
            chunks.addAll(c);
        }

        @Override
        public List<ScoredChunk> search(String query, int topK) {
            return chunks.stream().filter(c -> c.id().startsWith("project:") || c.id().startsWith("skills:"))
                    .limit(topK).map(c -> new ScoredChunk(c, score)).toList();
        }
    }

    private final FakeIndex index = new FakeIndex();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<List<KnowledgeChunk>> context = new AtomicReference<>();

    private AskService service(DraftAnswer draft) {
        AnswerGenerator generator = (question, language, ctx) -> {
            calls.incrementAndGet();
            context.set(ctx);
            assertThat(language).isEqualTo("es");
            return draft;
        };
        AskService service = new AskService(profile, index, generator, RetrievalPolicy.defaults());
        service.index();
        return service;
    }

    @Test
    void chunksCoverEveryEntityWithStableIdsAndNoPrivateData() {
        List<KnowledgeChunk> chunks = ProfileKnowledge.chunks(profile);

        assertThat(chunks).extracting(KnowledgeChunk::id).containsExactly(
                "person", "experience:acme", "project:kafka", "evidence:team", "lab:ai",
                "skills:backend", "skills:messaging");
        assertThat(chunks).allSatisfy(c -> assertThat(c.text()).doesNotContain("ada@example.com", "London"));
        assertThat(chunks.get(1).text()).contains("Ada Lovelace worked as Developer at ACME", "Title team");
        assertThat(chunks.get(4).text()).contains("Project kafka");
    }

    @Test
    void answersWithValidCitations() {
        Answer answer = service(new DraftAnswer(true, "Ada usa Kafka.", List.of("project:kafka", "skills:messaging")))
                .ask("¿Qué experiencia tiene con Kafka?");

        assertThat(answer.status()).isEqualTo(Answer.Status.ANSWERED);
        assertThat(answer.citations()).extracting(KnowledgeChunk::id).containsExactly("project:kafka", "skills:messaging");
        assertThat(answer.warnings()).isEmpty();
        assertThat(context.get()).extracting(KnowledgeChunk::id).doesNotContain("person");
    }

    @Test
    void noRelevantChunksMeansUnknownWithoutCallingTheModel() {
        index.score = 0.2;

        Answer answer = service(new DraftAnswer(true, "inventado", List.of("project:kafka"))).ask("¿Sabe cocinar?");

        assertThat(answer.status()).isEqualTo(Answer.Status.UNKNOWN);
        assertThat(calls).hasValue(0);
    }

    @Test
    void citationsOutsideRetrievedContextAreDropped() {
        Answer answer = service(new DraftAnswer(true, "Ada usa Kafka.", List.of("project:kafka", "evidence:team")))
                .ask("Kafka");

        assertThat(answer.citations()).extracting(KnowledgeChunk::id).containsExactly("project:kafka");
        assertThat(answer.warnings()).singleElement().asString().contains("evidence:team");
    }

    @Test
    void answerWithoutValidCitationsIsUnknown() {
        Answer answer = service(new DraftAnswer(true, "Ada usa Kafka.", List.of("ghost"))).ask("Kafka");

        assertThat(answer.status()).isEqualTo(Answer.Status.UNKNOWN);
        assertThat(answer.text()).isEmpty();
    }

    @Test
    void modelSayingItCannotAnswerIsUnknown() {
        assertThat(service(new DraftAnswer(false, "", List.of())).ask("Kafka").status())
                .isEqualTo(Answer.Status.UNKNOWN);
    }

    @Test
    void inventedNumbersAreWarnings() {
        Answer answer = service(new DraftAnswer(true, "Usó Kafka durante 7 años con 99% de uptime.", List.of("project:kafka")))
                .ask("Kafka");

        assertThat(answer.warnings()).anyMatch(w -> w.contains("99%"));
    }

    @Test
    void rejectsEmptyAndHugeQuestions() {
        AskService service = service(new DraftAnswer(false, "", List.of()));
        assertThatThrownBy(() -> service.ask("  a ")).isInstanceOf(InvalidQuestionException.class);
        assertThatThrownBy(() -> service.ask("x".repeat(501))).isInstanceOf(InvalidQuestionException.class);
        assertThatThrownBy(() -> service.ask(null)).isInstanceOf(InvalidQuestionException.class);
    }

    @Test
    void policyIsValidated() {
        assertThatThrownBy(() -> new RetrievalPolicy(0, 0.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetrievalPolicy(4, 1.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
