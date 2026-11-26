package dev.proofstack.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.Period;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileDate;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.Skill;
import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.port.out.ProfileJsonCodec;

class DraftImportServiceTest {

    /** Keeps profiles by name, so the test can see what the service handed back without a JSON library. */
    private static final class FakeCodec implements ProfileJsonCodec {
        final List<Profile> written = new ArrayList<>();
        Profile draft;

        @Override
        public Profile parse(String json) {
            if (!json.equals("draft")) {
                throw new ProfileValidationException(new Violation("$", "broken"));
            }
            return draft;
        }

        @Override
        public String toJson(Profile profile) {
            written.add(profile);
            return "json-" + written.size();
        }
    }

    private static Experience job(String id, String role, String company) {
        return new Experience(id, role, company, "", Period.ongoingSince(ProfileDate.of(2022)), List.of(), List.of(),
                List.of());
    }

    private static Project project(String id, String url) {
        return new Project(id, id, "A project", null, null, List.of(), List.of(), List.of(new Link("GitHub", url)),
                List.of(Area.BACKEND), List.of());
    }

    private static Profile profile(List<Experience> jobs, List<Project> projects, List<Skill> skills) {
        return new Profile(new PersonalInfo("Ada", "Backend Engineer", "Builds systems", "Spain", null, List.of()),
                jobs, List.of(), projects, skills, List.of(), List.of());
    }

    @Test
    void careerImportListsWhatIsNewAndWhatTheImportDoesNotHave() {
        FakeCodec codec = new FakeCodec();
        codec.draft = profile(List.of(job("old", "Waiter", "Hotel")), List.of(), List.of(new Skill("Java", Area.BACKEND)));
        Profile linkedIn = profile(List.of(job("acme", "Developer", "Acme")), List.of(),
                List.of(new Skill("java", Area.BACKEND), new Skill("Docker", Area.DEVOPS)));

        var imported = new DraftImportService(codec).career("draft", () -> linkedIn);

        assertThat(imported.json()).isEqualTo("json-1");
        assertThat(imported.added()).contains("Developer @ Acme", "Docker").doesNotContain("java");
        assertThat(imported.leftOut()).containsExactly("Waiter @ Hotel");
    }

    @Test
    void projectImportAddsOnlyRepositoriesTheDraftDoesNotHave() {
        FakeCodec codec = new FakeCodec();
        codec.draft = profile(List.of(), List.of(project("mine", "https://github.com/ada/mine")), List.of());

        var imported = new DraftImportService(codec).projects("draft", (account, forks) -> List.of(
                project("mine-copy", "http://github.com/ada/mine.git/"), project("fresh", "https://github.com/ada/fresh")),
                "ada");

        assertThat(imported.added()).containsExactly("fresh");
        assertThat(codec.written.getFirst().projects()).extracting(Project::id).containsExactly("mine", "fresh");
    }

    @Test
    void aBrokenDraftFailsBeforeAnyNetworkCall() {
        FakeCodec codec = new FakeCodec();
        AtomicBoolean called = new AtomicBoolean();
        assertThatThrownBy(() -> new DraftImportService(codec).career("half-typed", () -> {
            called.set(true);
            return null;
        })).isInstanceOf(ProfileValidationException.class);
        assertThat(called).isFalse();
    }
}
