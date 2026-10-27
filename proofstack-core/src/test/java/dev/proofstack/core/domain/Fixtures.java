package dev.proofstack.core.domain;

import java.time.YearMonth;
import java.util.List;

/** Test data builders. */
public final class Fixtures {

    private Fixtures() {
    }

    public static PersonalInfo person() {
        return new PersonalInfo("Ada Lovelace", "Backend Engineer", "Summary", "London", "ada@example.com",
                List.of(new Link("GitHub", "https://github.com/example")));
    }

    public static Project project(String id, Area... areas) {
        return new Project(id, "Project " + id, "Summary " + id, "Problem", "Solution",
                List.of("Java"), List.of("Learning"), List.of(), List.of(areas), List.of());
    }

    public static Evidence evidence(String id, EvidenceCategory category) {
        return new Evidence(id, category, "Title " + id, "Context", "Action", "Result", null, List.of());
    }

    public static Experience experience(String id, List<String> evidenceIds) {
        return new Experience(id, "Developer", "ACME", null, Period.ongoingSince(YearMonth.of(2022, 1)),
                List.of("Did things"), List.of("Java"), evidenceIds);
    }

    public static Profile profile(List<Project> projects, List<Evidence> evidence, List<Lab> labs,
                                  List<Skill> skills) {
        return new Profile(person(), List.of(), List.of(), projects, skills, evidence, labs);
    }
}
