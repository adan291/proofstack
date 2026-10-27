package dev.proofstack.core.domain;

import java.util.List;

/**
 * A project told as evidence: what was the problem, how it was solved and what was learned.
 * {@code problem}, {@code solution} are optional so small experiments can still be listed.
 */
public record Project(
        String id,
        String name,
        String summary,
        String problem,
        String solution,
        List<String> techStack,
        List<String> learnings,
        List<Link> links,
        List<Area> areas,
        List<String> evidenceIds) {

    public Project {
        id = Checks.requireText(id, "id");
        name = Checks.requireText(name, "name");
        summary = Checks.requireText(summary, "summary");
        problem = Checks.optionalText(problem);
        solution = Checks.optionalText(solution);
        techStack = Checks.texts(techStack, "techStack");
        learnings = Checks.texts(learnings, "learnings");
        links = Checks.list(links, "links");
        areas = Checks.list(areas, "areas").stream().distinct().toList();
        evidenceIds = Checks.texts(evidenceIds, "evidence");
    }
}
