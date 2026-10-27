package dev.proofstack.core.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Aggregate root. Validates cross-entity invariants (unique ids, resolvable references)
 * and reports <b>all</b> problems at once through {@link ProfileValidationException}.
 */
public record Profile(
        String language,
        PersonalInfo person,
        List<Experience> experience,
        List<Education> education,
        List<Project> projects,
        List<Skill> skills,
        List<Evidence> evidence,
        List<Lab> labs) {

    public static final String DEFAULT_LANGUAGE = "en";

    /** Profile written in the default language ({@value #DEFAULT_LANGUAGE}). */
    public Profile(PersonalInfo person, List<Experience> experience, List<Education> education,
                   List<Project> projects, List<Skill> skills, List<Evidence> evidence, List<Lab> labs) {
        this(DEFAULT_LANGUAGE, person, experience, education, projects, skills, evidence, labs);
    }

    public Profile {
        language = language == null || language.isBlank() ? DEFAULT_LANGUAGE : language.strip();
        if (!language.matches("^[a-z]{2}(-[A-Z]{2})?$")) {
            throw new ProfileValidationException(new Violation("$.language", "must be a BCP 47 tag like 'en' or 'es-ES'"));
        }
        Checks.requireNonNull(person, "person");
        experience = Checks.list(experience, "experience");
        education = Checks.list(education, "education");
        projects = Checks.list(projects, "projects");
        skills = Checks.list(skills, "skills");
        evidence = Checks.list(evidence, "evidence");
        labs = Checks.list(labs, "labs");

        List<Violation> violations = new ArrayList<>();
        uniqueIds("experience", experience, Experience::id, violations);
        uniqueIds("education", education, Education::id, violations);
        Set<String> projectIds = uniqueIds("projects", projects, Project::id, violations);
        Set<String> evidenceIds = uniqueIds("evidence", evidence, Evidence::id, violations);
        uniqueIds("labs", labs, Lab::id, violations);

        for (int i = 0; i < experience.size(); i++) {
            checkRefs("$.experience[" + i + "].evidence", experience.get(i).evidenceIds(), evidenceIds, "evidence", violations);
        }
        for (int i = 0; i < projects.size(); i++) {
            checkRefs("$.projects[" + i + "].evidence", projects.get(i).evidenceIds(), evidenceIds, "evidence", violations);
        }
        for (int i = 0; i < labs.size(); i++) {
            checkRefs("$.labs[" + i + "].projects", labs.get(i).projectIds(), projectIds, "project", violations);
        }

        if (!violations.isEmpty()) {
            throw new ProfileValidationException(violations);
        }
    }

    public Optional<Project> project(String id) {
        return projects.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public Optional<Evidence> evidence(String id) {
        return evidence.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    private static <T> Set<String> uniqueIds(String collection, List<T> items, Function<T, String> id,
                                             List<Violation> violations) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            String value = id.apply(items.get(i));
            if (!seen.add(value)) {
                violations.add(new Violation("$." + collection + "[" + i + "].id", "duplicate id '" + value + "'"));
            }
        }
        return seen;
    }

    private static void checkRefs(String path, List<String> refs, Set<String> known, String kind,
                                  List<Violation> violations) {
        for (int i = 0; i < refs.size(); i++) {
            if (!known.contains(refs.get(i))) {
                violations.add(new Violation(path + "[" + i + "]", "unknown " + kind + " id '" + refs.get(i) + "'"));
            }
        }
    }
}
