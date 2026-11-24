package dev.proofstack.core.domain;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileMergeTest {

    private static Experience job(String id, String company, String highlight, List<String> evidence, boolean current) {
        Period period = current ? Period.ongoingSince(YearMonth.of(2022, 1))
                : new Period(YearMonth.of(2018, 1), YearMonth.of(2021, 12));
        return new Experience(id, "Backend Developer", company, null, period, List.of(highlight),
                List.of("Java"), evidence);
    }

    private final Profile mine = new Profile("es",
            new PersonalInfo("Adán Sánchez Manzano", "Backend Developer", "Mi resumen", "España", null,
                    List.of(new Link("GitHub", "https://github.com/adan291"))),
            List.of(job("current-job", "TODO: empresa", "Migración Tomcat 9 → 10", List.of("team"), true)),
            List.of(new Education("dam", "DAM", "TODO: centro", Period.ongoingSince(YearMonth.of(2018, 9)), List.of())),
            List.of(Fixtures.project("proofstack", Area.BACKEND)),
            List.of(new Skill("Java", Area.BACKEND)),
            List.of(Fixtures.evidence("team", EvidenceCategory.LEADERSHIP)),
            List.of());

    private final Profile linkedIn = new Profile("en",
            new PersonalInfo("Adán Sánchez", "Backend Developer at ACME", "LinkedIn summary", "Madrid", null,
                    List.of(new Link("github.com", "https://github.com/adan291/"), new Link("blog", "https://adan.dev"))),
            List.of(job("acme", "ACME", "TODO: add 1–3 concrete achievements", List.of(), true),
                    job("old-co", "Old Co", "Mantenimiento de APIs REST", List.of(), false)),
            List.of(new Education("ies-x", "CFGS DAM", "IES Ejemplo", new Period(YearMonth.of(2018, 9), YearMonth.of(2020, 6)), List.of())),
            List.of(Fixtures.project("busqueda")),
            List.of(new Skill("java", Area.BACKEND), new Skill("Kafka", Area.MESSAGING)),
            List.of(), List.of());

    @Test
    void careerComesFromLinkedInAndProofsStayMine() {
        Profile merged = ProfileMerge.careerFrom(mine, linkedIn);

        assertThat(merged.language()).isEqualTo("es");
        assertThat(merged.person().name()).isEqualTo("Adán Sánchez Manzano");
        assertThat(merged.person().summary()).isEqualTo("Mi resumen");
        assertThat(merged.person().links()).extracting(Link::url)
                .containsExactly("https://github.com/adan291", "https://adan.dev");

        assertThat(merged.experience()).extracting(Experience::company).containsExactly("ACME", "Old Co");
        Experience current = merged.experience().getFirst();
        assertThat(current.evidenceIds()).containsExactly("team");
        assertThat(current.highlights()).containsExactly("Migración Tomcat 9 → 10");

        assertThat(merged.education()).extracting(Education::institution).containsExactly("IES Ejemplo");
        assertThat(merged.projects()).extracting(Project::id).containsExactly("proofstack", "busqueda");
        assertThat(merged.skills()).extracting(Skill::name).containsExactly("Java", "Kafka");
        assertThat(merged.evidence()).isEqualTo(mine.evidence());
    }

    @Test
    void emptyImportKeepsEverything() {
        Profile empty = new Profile(new PersonalInfo("X", "Y", null, null, null, List.of()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        Profile merged = ProfileMerge.careerFrom(mine, empty);

        assertThat(merged.experience()).isEqualTo(mine.experience());
        assertThat(merged.education()).isEqualTo(mine.education());
    }
}
