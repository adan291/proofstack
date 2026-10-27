package dev.proofstack.core.domain.readiness;

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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
