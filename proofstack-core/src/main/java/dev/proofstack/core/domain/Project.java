package dev.proofstack.core.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A project told as evidence: what was the problem, how it was solved and what was learned.
 * {@code problem}, {@code solution} are optional so small experiments can still be listed.
 * {@code featured} is the author's choice of what the site shows first; when no project is marked, the site picks.
 * {@code areaNotes} says, per area, what this project proves in that area. The first of {@code areas} is the
 * project's main area: the site shows the full card there and a short card with the note everywhere else.
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
        List<String> evidenceIds,
        boolean featured,
        Map<Area, String> areaNotes) {

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
        Map<Area, String> notes = new EnumMap<>(Area.class);
        if (areaNotes != null) {
            areaNotes.forEach((area, note) -> {
                String text = Checks.optionalText(note);
                if (area != null && text != null) {
                    notes.put(area, text);
                }
            });
        }
        areaNotes = Collections.unmodifiableMap(notes);
    }

    /** A project the author has not marked as featured. */
    public Project(String id, String name, String summary, String problem, String solution, List<String> techStack,
                   List<String> learnings, List<Link> links, List<Area> areas, List<String> evidenceIds) {
        this(id, name, summary, problem, solution, techStack, learnings, links, areas, evidenceIds, false);
    }

    /** A project without per-area notes. */
    public Project(String id, String name, String summary, String problem, String solution, List<String> techStack,
                   List<String> learnings, List<Link> links, List<Area> areas, List<String> evidenceIds,
                   boolean featured) {
        this(id, name, summary, problem, solution, techStack, learnings, links, areas, evidenceIds, featured, Map.of());
    }
}
