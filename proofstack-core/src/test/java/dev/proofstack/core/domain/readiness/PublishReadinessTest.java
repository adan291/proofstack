package dev.proofstack.core.domain.readiness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.EvidenceCategory;
import dev.proofstack.core.domain.Fixtures;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.Skill;
import dev.proofstack.core.domain.StarterProfile;
import dev.proofstack.core.domain.readiness.PublishReadiness.Finding;
import dev.proofstack.core.domain.readiness.PublishReadiness.Severity;

class PublishReadinessTest {

    @Test
    void starterProfileIsValidButNotPublishable() {
        Profile starter = StarterProfile.create("Ada Lovelace", "Backend Engineer", "es");

        List<Finding> findings = PublishReadiness.check(starter);

        assertThat(starter.language()).isEqualTo("es");
        assertThat(PublishReadiness.hasErrors(findings)).isTrue();
        assertThat(findings).extracting(Finding::path)
                .contains("$.person.summary", "$.projects[0].problem", "$.evidence[0].result");
        assertThat(findings.getFirst().severity()).isEqualTo(Severity.ERROR);
        // The starter evidence is linked from the starter project, so no "unlinked evidence" warning.
        assertThat(findings).noneMatch(f -> f.message().contains("not linked"));
    }

    @Test
    void placeholdersAreFoundInEveryFieldThatIsPublished() {
        String todo = "TODO: fill";
        var job = new dev.proofstack.core.domain.Experience("acme", "Developer", "ACME", todo,
                dev.proofstack.core.domain.Period.ongoingSince(java.time.YearMonth.of(2022, 1)),
                List.of("Did things"), List.of("Java", todo), List.of());
        Project project = new Project("billing", "Billing", "Outbox", "Deploys", "Outbox table",
                List.of(todo), List.of(), List.of(new Link(todo, "https://github.com/ada/billing")),
                List.of(Area.BACKEND), List.of());
        Profile profile = new Profile("en", Fixtures.person(), List.of(job), List.of(), List.of(project),
                List.of(new Skill(todo, Area.BACKEND)), List.of(),
                List.of(new dev.proofstack.core.domain.Lab("lab", todo, dev.proofstack.core.domain.LabType.AI,
                        "Real description", List.of())),
                List.of(new dev.proofstack.core.domain.Language(todo, todo)),
                List.of(new dev.proofstack.core.domain.Certification(todo, todo, null,
                        new Link(todo, "https://example.com/credential/123"))));

        List<String> errors = PublishReadiness.check(profile).stream()
                .filter(f -> f.severity() == Severity.ERROR).map(Finding::path).toList();

        assertThat(errors).contains(
                "$.experience[0].location", "$.experience[0].technologies[1]",
                "$.projects[0].techStack[0]", "$.projects[0].links[0].label",
                "$.skills[0].name", "$.labs[0].name",
                "$.languages[0].name", "$.languages[0].level",
                "$.certifications[0].title", "$.certifications[0].issuer",
                "$.certifications[0].link.label", "$.certifications[0].link.url");
        assertThat(dev.proofstack.core.domain.TodoCounter.count(profile)).isEqualTo(11);
    }

    /** doctor and the TODO counter must look at the same fields, or one says "ready" while the other counts TODOs. */
    @Test
    void doctorAndTheTodoCounterAgree() {
        Profile profile = new Profile("en",
                new PersonalInfo("Ada", "Backend", "Summary", null, null,
                        List.of(new Link("GitHub", "https://github.com/ada/TODO-repo"))),
                List.of(), List.of(), List.of(), List.of(new Skill("Java", Area.BACKEND)), List.of(), List.of());

        long placeholders = PublishReadiness.check(profile).stream()
                .filter(f -> f.message().startsWith("placeholder")).count();

        assertThat(placeholders).isEqualTo(dev.proofstack.core.domain.TodoCounter.count(profile)).isEqualTo(1);
    }

    @Test
    void completeProfileHasNoErrors() {
        Project project = new Project("billing", "Billing events", "Outbox + Kafka", "Monolith deploys",
                "Outbox table and Kafka consumer", List.of("Java"), List.of("Read model first"),
                List.of(new Link("GitHub", "https://github.com/ada/billing")), List.of(Area.MESSAGING), List.of("team"));
        Profile profile = new Profile("en",
                new PersonalInfo("Ada", "Backend", "I build boring systems.", null, null,
                        List.of(new Link("GitHub", "https://github.com/ada"))),
                List.of(Fixtures.experience("acme", List.of())), List.of(), List.of(project),
                List.of(new Skill("Kafka", Area.MESSAGING)),
                List.of(Fixtures.evidence("team", EvidenceCategory.LEADERSHIP)), List.of());

        List<Finding> findings = PublishReadiness.check(profile);

        assertThat(PublishReadiness.hasErrors(findings)).as(findings.toString()).isFalse();
        assertThat(findings).isEmpty();
    }

    @Test
    void flagsSampleAndGenericUrls() {
        assertThat(PublishReadiness.isPlaceholderUrl("https://github.com/")).isTrue();
        assertThat(PublishReadiness.isPlaceholderUrl("https://www.linkedin.com/")).isTrue();
        assertThat(PublishReadiness.isPlaceholderUrl("https://www.linkedin.com/in/")).isTrue();
        assertThat(PublishReadiness.isPlaceholderUrl("https://github.com/example")).isFalse();
        assertThat(PublishReadiness.isPlaceholderUrl("https://docs.example.com/x")).isTrue();
        assertThat(PublishReadiness.isPlaceholderUrl("mailto:jane@example.com")).isTrue();
        assertThat(PublishReadiness.isPlaceholderUrl("https://ada.dev")).isFalse();
    }

    @Test
    void warnsAboutProjectsWithoutProblemLinksAndUnlinkedEvidence() {
        Profile profile = Fixtures.profile(
                List.of(new Project("p", "P", "Summary", null, null, List.of(), List.of(), List.of(),
                        List.of(), List.of())),
                List.of(Fixtures.evidence("orphan", EvidenceCategory.TECHNICAL)), List.of(), List.of());

        List<Finding> findings = PublishReadiness.check(profile);

        assertThat(findings).filteredOn(f -> f.severity() == Severity.WARNING).extracting(Finding::path)
                .contains("$.projects[0]", "$.projects[0].links", "$.evidence[0]", "$.experience", "$.skills");
    }
}
