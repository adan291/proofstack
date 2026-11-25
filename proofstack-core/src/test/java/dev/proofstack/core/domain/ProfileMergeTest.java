package dev.proofstack.core.domain;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

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

    /** LinkedIn had no real dates: the hand-written job's period is the only real one, so it wins; else stay flagged. */
    @Test
    void anImportedPeriodFlaggedAsUnknownNeverHidesItsFlag() {
        String flag = TodoCounter.DATES + ": LinkedIn only gives the year";
        Experience imported = new Experience("acme", "Backend Developer", "ACME", null,
                Period.ongoingSince(YearMonth.of(2020, 1)), List.of("TODO: add achievements", flag), List.of(), List.of());
        Experience fresh = new Experience("new-co", "Developer", "New Co", null,
                Period.ongoingSince(YearMonth.of(2023, 1)), List.of("TODO: add achievements", flag), List.of(), List.of());
        Profile withFlags = new Profile("en", linkedIn.person(), List.of(imported, fresh), List.of(), List.of(),
                List.of(), List.of(), List.of());

        Profile merged = ProfileMerge.careerFrom(mine, withFlags);

        Experience matched = merged.experience().get(0); // matched with the current job whose company was TODO
        assertThat(matched.period()).isEqualTo(mine.experience().getFirst().period());
        assertThat(matched.highlights()).noneMatch(h -> h.startsWith(TodoCounter.DATES));
        assertThat(merged.experience().get(1).highlights()).contains(flag);
    }

    @Test
    void theSameCompanyWrittenDifferentlyIsStillTheSameJob() {
        Profile handWritten = new Profile("en", mine.person(),
                List.of(job("example", "Example Corp", "Split the billing monolith", List.of("team"), true)),
                List.of(), List.of(), List.of(), mine.evidence(), List.of());
        Profile imported = new Profile("en", linkedIn.person(),
                List.of(job("example-corporation-s-l", "Example Corporation, S.L.", "Billing", List.of(), true)),
                List.of(), List.of(), List.of(), List.of(), List.of());

        ProfileMerge.Result result = ProfileMerge.merge(handWritten, imported);

        assertThat(result.profile().experience().getFirst().evidenceIds()).containsExactly("team");
        assertThat(result.notInImport()).isEmpty();
    }

    /** LinkedIn owns the career (ADR-012), but a job it does not list must not vanish silently with its evidence. */
    @Test
    void handWrittenJobsTheImportDoesNotHaveAreReported() {
        Profile handWritten = new Profile("en", mine.person(),
                List.of(job("side", "Freelance", "Built a shop", List.of("team"), false)),
                List.of(), List.of(), List.of(), mine.evidence(), List.of());

        ProfileMerge.Result result = ProfileMerge.merge(handWritten, linkedIn);

        assertThat(result.profile().experience()).extracting(Experience::company).containsExactly("ACME", "Old Co");
        assertThat(result.notInImport()).extracting(Experience::id).containsExactly("side");
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
