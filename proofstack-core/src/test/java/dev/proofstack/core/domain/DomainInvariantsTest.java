package dev.proofstack.core.domain;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static dev.proofstack.core.domain.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainInvariantsTest {

    @Test
    void periodRejectsEndBeforeStart() {
        assertThatThrownBy(() -> new Period(YearMonth.of(2024, 5), YearMonth.of(2024, 4)))
                .isInstanceOfSatisfying(InvalidValueException.class,
                        e -> assertThat(e.field()).isEqualTo("endDate"));
    }

    @Test
    void periodWithoutEndIsOngoing() {
        assertThat(Period.ongoingSince(YearMonth.of(2024, 1)).isOngoing()).isTrue();
    }

    @Test
    void personalInfoRequiresName() {
        assertThatThrownBy(() -> new PersonalInfo("  ", "Backend", null, null, null, List.of()))
                .isInstanceOfSatisfying(InvalidValueException.class,
                        e -> assertThat(e.field()).isEqualTo("name"));
    }

    @Test
    void personalInfoRejectsInvalidEmail() {
        assertThatThrownBy(() -> new PersonalInfo("Ada", "Backend", null, null, "not-an-email", List.of()))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void linkRejectsDangerousSchemes() {
        assertThatThrownBy(() -> new Link("x", "javascript:alert(1)"))
                .isInstanceOfSatisfying(InvalidValueException.class,
                        e -> assertThat(e.field()).isEqualTo("url"));
        assertThat(new Link("mail", "mailto:a@b.dev").url()).isEqualTo("mailto:a@b.dev");
    }

    @Test
    void textsAreTrimmed() {
        Skill skill = new Skill("  Java  ", Area.BACKEND);
        assertThat(skill.name()).isEqualTo("Java");
    }

    @Test
    void evidenceDerivesAreasFromCategory() {
        assertThat(evidence("e1", EvidenceCategory.MENTORING).areas()).containsExactly(Area.LEADERSHIP);
        assertThat(evidence("e2", EvidenceCategory.DOCUMENTATION).areas()).containsExactly(Area.COMMUNICATION);
        assertThat(evidence("e3", EvidenceCategory.TECHNICAL).areas()).isEmpty();
    }

    @Test
    void collectionsAreImmutable() {
        Project p = project("p1", Area.BACKEND);
        assertThatThrownBy(() -> p.techStack().add("Kotlin")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void profileReportsAllViolationsAtOnce() {
        Lab lab = new Lab("lab", "AI Lab", LabType.AI, "Experiments", List.of("missing-project"));
        Experience exp = experience("x1", List.of("missing-evidence"));

        assertThatThrownBy(() -> new Profile(person(), List.of(exp), List.of(),
                List.of(project("p1"), project("p1")), List.of(), List.of(), List.of(lab)))
                .isInstanceOfSatisfying(ProfileValidationException.class, e ->
                        assertThat(e.violations()).extracting(Violation::path).containsExactlyInAnyOrder(
                                "$.projects[1].id",
                                "$.experience[0].evidence[0]",
                                "$.labs[0].projects[0]"));
    }

    @Test
    void validProfileIsAccepted() {
        Profile profile = new Profile(person(), List.of(experience("x1", List.of("e1"))), List.of(),
                List.of(project("p1")), List.of(), List.of(evidence("e1", EvidenceCategory.LEADERSHIP)), List.of());
        assertThat(profile.evidence("e1")).isPresent();
        assertThat(profile.experience().getFirst().current()).isTrue();
    }
}
