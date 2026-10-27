package dev.proofstack.core.domain.knowledge;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.Education;
import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.Lab;
import dev.proofstack.core.domain.Period;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.Skill;
import dev.proofstack.core.domain.knowledge.KnowledgeChunk.ChunkType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Splits a profile into retrievable chunks: one per entity, plus one per skill area.
 * Each chunk is self-contained (it repeats the person's name and the entity title) so it
 * still makes sense when retrieved alone. Email and location are never included.
 */
public final class ProfileKnowledge {

    private ProfileKnowledge() {
    }

    public static List<KnowledgeChunk> chunks(Profile profile) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        PersonalInfo person = profile.person();
        String who = person.name();

        chunks.add(new KnowledgeChunk("person", ChunkType.PERSON, who,
                lines(who + " — " + person.headline(), person.summary())));

        for (Experience e : profile.experience()) {
            chunks.add(new KnowledgeChunk("experience:" + e.id(), ChunkType.EXPERIENCE, e.role() + " · " + e.company(),
                    lines(who + " worked as " + e.role() + " at " + e.company() + " (" + period(e.period()) + ").",
                            bullets("Highlights", e.highlights()),
                            list("Technologies", e.technologies()),
                            evidenceTitles(profile, e.evidenceIds()))));
        }
        for (Education e : profile.education()) {
            chunks.add(new KnowledgeChunk("education:" + e.id(), ChunkType.EDUCATION, e.title(),
                    lines(who + " studied " + e.title() + " at " + e.institution() + " (" + period(e.period()) + ").",
                            bullets("Highlights", e.highlights()))));
        }
        for (Project p : profile.projects()) {
            chunks.add(new KnowledgeChunk("project:" + p.id(), ChunkType.PROJECT, p.name(),
                    lines("Project by " + who + ": " + p.name() + ". " + p.summary(),
                            prefixed("Problem", p.problem()),
                            prefixed("Solution", p.solution()),
                            list("Tech stack", p.techStack()),
                            bullets("Learnings", p.learnings()),
                            list("Areas", p.areas().stream().map(Area::label).toList()),
                            evidenceTitles(profile, p.evidenceIds()))));
        }
        for (Evidence e : profile.evidence()) {
            chunks.add(new KnowledgeChunk("evidence:" + e.id(), ChunkType.EVIDENCE, e.title(),
                    lines("Evidence of " + e.category().name().toLowerCase(Locale.ROOT) + " by " + who + ": " + e.title(),
                            prefixed("Context", e.context()),
                            prefixed("Action", e.action()),
                            prefixed("Result", e.result()))));
        }
        for (Lab l : profile.labs()) {
            List<String> projectNames = l.projectIds().stream().map(profile::project)
                    .flatMap(java.util.Optional::stream).map(Project::name).toList();
            chunks.add(new KnowledgeChunk("lab:" + l.id(), ChunkType.LAB, l.name(),
                    lines(who + "'s " + l.name() + " (" + l.type().name().toLowerCase(Locale.ROOT) + " lab). "
                            + l.description(), list("Projects", projectNames))));
        }
        for (Area area : Area.values()) {
            List<String> names = profile.skills().stream().filter(s -> s.area() == area).map(Skill::name).toList();
            if (!names.isEmpty()) {
                chunks.add(new KnowledgeChunk("skills:" + area.name().toLowerCase(Locale.ROOT), ChunkType.SKILLS,
                        "Skills · " + area.label(),
                        who + " lists these " + area.label() + " skills: " + String.join(", ", names) + "."));
            }
        }
        return List.copyOf(chunks);
    }

    private static String evidenceTitles(Profile profile, List<String> ids) {
        return list("Backed by evidence", ids.stream().map(profile::evidence)
                .flatMap(java.util.Optional::stream).map(Evidence::title).toList());
    }

    private static String period(Period p) {
        return p.start() + " – " + p.endDate().map(Object::toString).orElse("present");
    }

    private static String prefixed(String label, String value) {
        return value == null ? null : label + ": " + value;
    }

    private static String list(String label, List<String> values) {
        return values.isEmpty() ? null : label + ": " + String.join(", ", values) + ".";
    }

    private static String bullets(String label, List<String> values) {
        return values.isEmpty() ? null
                : label + ":\n" + values.stream().map(v -> "- " + v).collect(Collectors.joining("\n"));
    }

    private static String lines(String... parts) {
        return java.util.Arrays.stream(parts).filter(Objects::nonNull).filter(s -> !s.isBlank())
                .collect(Collectors.joining("\n"));
    }
}
