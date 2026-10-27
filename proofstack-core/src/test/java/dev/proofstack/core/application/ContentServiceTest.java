package dev.proofstack.core.application;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.EvidenceCategory;
import dev.proofstack.core.domain.Fixtures;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.content.ContentBrief;
import dev.proofstack.core.domain.content.ContentFormat;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentSubject;
import dev.proofstack.core.domain.content.GeneratedContent;
import dev.proofstack.core.port.out.ContentGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentServiceTest {

    private static final Path PROFILE = Path.of("profile.yaml");

    private final Project project = new Project("tomcat", "Tomcat 9 → 10", "Migración a Jakarta", "javax", "jakarta",
            List.of("Java 25"), List.of(), List.of(), List.of(Area.BACKEND), List.of("team"));
    private final Profile profile = new Profile("es", Fixtures.person(), List.of(), List.of(), List.of(project),
            List.of(), List.of(Fixtures.evidence("team", EvidenceCategory.LEADERSHIP)), List.of());

    private final AtomicReference<ContentBrief> received = new AtomicReference<>();

    private ContentService service(String body) {
        ContentGenerator generator = brief -> {
            received.set(brief);
            return new GeneratedContent(brief.format(), "Title", body, List.of("java"), List.of());
        };
        return new ContentService(location -> profile, generator);
    }

    @Test
    void buildsBriefWithSubjectRelatedEvidenceAndProfileLanguage() {
        service("Migré de Tomcat 9 a 10.").generate(PROFILE, "tomcat", ContentFormat.LINKEDIN_POST, null);

        ContentBrief brief = received.get();
        assertThat(brief.language()).isEqualTo("es");
        assertThat(brief.subject()).isInstanceOf(ContentSubject.ProjectSubject.class);
        assertThat(brief.relatedEvidence()).extracting("id").containsExactly("team");
    }

    @Test
    void explicitLanguageOverridesProfile() {
        service("ok body").generate(PROFILE, "tomcat", ContentFormat.README, "en");
        assertThat(received.get().language()).isEqualTo("en");
    }

    @Test
    void evidenceCanBeASubject() {
        service("ok body").generate(PROFILE, "team", ContentFormat.VIDEO_SCRIPT, null);
        assertThat(received.get().subject()).isInstanceOf(ContentSubject.EvidenceSubject.class);
    }

    @Test
    void flagsInventedNumbersAsWarnings() {
        GeneratedContent content = service("Migré de Tomcat 9 a 10 y reduje errores un 37%.")
                .generate(PROFILE, "tomcat", ContentFormat.LINKEDIN_POST, null);

        assertThat(content.warnings()).singleElement().asString().contains("37%");
    }

    @Test
    void unknownSubjectListsAvailableIds() {
        assertThatThrownBy(() -> service("x").generate(PROFILE, "nope", ContentFormat.README, null))
                .isInstanceOfSatisfying(ContentGenerationException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ContentGenerationException.Kind.UNKNOWN_SUBJECT);
                    assertThat(e.getMessage()).contains("tomcat", "team");
                });
    }

    @Test
    void enforcesPlatformLength() {
        String tooLong = "a".repeat(3001);
        assertThatThrownBy(() -> service(tooLong).generate(PROFILE, "tomcat", ContentFormat.LINKEDIN_POST, null))
                .isInstanceOfSatisfying(ContentGenerationException.class,
                        e -> assertThat(e.kind()).isEqualTo(ContentGenerationException.Kind.INVALID_OUTPUT));
    }

    @Test
    void generatedContentNormalizesHashtagsAndRendersMarkdown() {
        GeneratedContent c = new GeneratedContent(ContentFormat.LINKEDIN_POST, "T", " body ",
                List.of("java", "#Spring Boot", "java"), List.of());

        assertThat(c.hashtags()).containsExactly("#java", "#SpringBoot");
        assertThat(c.toMarkdown()).isEqualTo("# T\n\nbody\n\n#java #SpringBoot\n");
    }

    @Test
    void emptyBodyIsInvalidOutput() {
        assertThatThrownBy(() -> new GeneratedContent(ContentFormat.README, "T", "  ", List.of(), List.of()))
                .isInstanceOf(ContentGenerationException.class);
    }

    @Test
    void formatsResolveFromSlug() {
        assertThat(ContentFormat.fromSlug("LinkedIn-Post")).contains(ContentFormat.LINKEDIN_POST);
        assertThat(ContentFormat.fromSlug("tweet")).isEmpty();
    }
}
