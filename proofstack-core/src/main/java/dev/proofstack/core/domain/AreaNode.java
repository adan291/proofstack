package dev.proofstack.core.domain;

import java.util.List;

/** One node of the architecture map with everything that backs it up. */
public record AreaNode(
        Area area,
        List<Skill> skills,
        List<Project> projects,
        List<Evidence> evidence,
        List<Lab> labs) {

    public AreaNode {
        Checks.requireNonNull(area, "area");
        skills = Checks.list(skills, "skills");
        projects = Checks.list(projects, "projects");
        evidence = Checks.list(evidence, "evidence");
        labs = Checks.list(labs, "labs");
    }

    public boolean isEmpty() {
        return skills.isEmpty() && projects.isEmpty() && evidence.isEmpty() && labs.isEmpty();
    }
}
