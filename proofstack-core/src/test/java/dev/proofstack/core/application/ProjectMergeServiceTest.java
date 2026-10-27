package dev.proofstack.core.application;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.AreaClassifier;
import dev.proofstack.core.domain.Fixtures;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.knowledge.KeywordSearch;
import dev.proofstack.core.domain.knowledge.KnowledgeChunk;
import dev.proofstack.core.domain.knowledge.ProfileKnowledge;
import dev.proofstack.core.port.in.MergeProjectsUseCase.MergeReport;
import dev.proofstack.core.port.out.ProfileWriter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectMergeServiceTest {

    private static Project project(String id, String url) {
        return new Project(id, "Project " + id, "Summary", null, null, List.of(), List.of(),
                url == null ? List.of() : List.of(new Link("GitHub", url)), List.of(), List.of());
    }

    private final Profile profile = Fixtures.profile(
            List.of(project("proofstack", null), project("search", "https://github.com/ada/semantic-search")),
            List.of(), List.of(), List.of());

    @Test
    void addsOnlyUnknownProjectsAndNeverTouchesOriginal() {
        AtomicReference<Profile> written = new AtomicReference<>();
        ProfileWriter writer = (p, target, overwrite) -> written.set(p);
        var service = new ProjectMergeService(path -> profile, (account, forks) -> List.of(
                project("proofstack", "https://github.com/ada/proofstack"),          // same id
                project("semantic-search", "http://GitHub.com/ada/semantic-search/"), // same URL, normalized
                project("esp32-coffee", "https://github.com/ada/esp32-coffee")), writer);

        MergeReport report = service.merge(Path.of("profile.yaml"), "ada", false, Path.of("profile.github.yaml"), false);

        assertThat(report.added()).containsExactly("esp32-coffee");
        assertThat(report.skipped()).containsExactly("proofstack", "semantic-search");
        assertThat(written.get().projects()).extracting(Project::id).containsExactly("proofstack", "search", "esp32-coffee");
    }

    @Test
    void refusesToWriteOverTheOriginalProfile() {
        var service = new ProjectMergeService(path -> profile, (a, f) -> List.of(), (p, t, o) -> { });

        assertThatThrownBy(() -> service.merge(Path.of("x/profile.yaml"), "ada", false, Path.of("x/../x/profile.yaml"), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void classifierMatchesWholeWordsForShortKeywords() {
        assertThat(AreaClassifier.match("spring-ai")).contains(Area.AI);
        assertThat(AreaClassifier.match("Maintenance")).isEmpty();
        assertThat(AreaClassifier.classify("Java")).isEqualTo(Area.BACKEND);
        assertThat(AreaClassifier.match("mqtt")).contains(Area.MESSAGING);
    }

    @Test
    void keywordSearchIgnoresAccentsAndRanksTitleMatches() {
        List<KnowledgeChunk> chunks = ProfileKnowledge.chunks(Fixtures.profile(
                List.of(new Project("tomcat", "Migración Tomcat", "Paso a Jakarta EE", null, null, List.of("Java"),
                        List.of(), List.of(), List.of(), List.of()), project("other", null)),
                List.of(), List.of(), List.of()));

        var hits = KeywordSearch.search(chunks, "¿Qué migracion de tomcat hizo?", 3);

        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst().chunk().id()).isEqualTo("project:tomcat");
        assertThat(KeywordSearch.search(chunks, "paella", 3)).isEmpty();
        assertThat(KeywordSearch.search(chunks, "   ", 3)).isEmpty();
    }
}
