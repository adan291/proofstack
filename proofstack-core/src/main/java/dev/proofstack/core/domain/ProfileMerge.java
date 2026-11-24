package dev.proofstack.core.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Updates a hand-written profile with career data imported from a platform (LinkedIn).
 *
 * <p>Rule of ownership:
 * <ul>
 *   <li><b>The platform owns the career</b>: experience, education, skills, languages and certifications come from it\n *       (when it has any; otherwise yours are kept).</li>
 *   <li><b>The profile owns the proofs and the presentation</b>: projects, evidence, labs, language and
 *       personal info are kept; imported values only fill fields that still contain a placeholder.</li>
 * </ul>
 * Links from experience to evidence survive the update: an imported job inherits the evidence ids and
 * technologies of the existing job it replaces (same company, or the current job when the company was still a placeholder).
 */
public final class ProfileMerge {

    private ProfileMerge() {
    }

    public static Profile careerFrom(Profile existing, Profile imported) {
        return new Profile(existing.language(), person(existing.person(), imported.person()),
                experience(existing.experience(), imported.experience()),
                imported.education().isEmpty() ? existing.education() : imported.education(),
                projects(existing.projects(), imported.projects()),
                skills(existing.skills(), imported.skills()),
                existing.evidence(), existing.labs(),
                imported.languages().isEmpty() ? existing.languages() : imported.languages(),
                imported.certifications().isEmpty() ? existing.certifications() : imported.certifications());
    }

    private static PersonalInfo person(PersonalInfo mine, PersonalInfo theirs) {
        List<Link> links = new ArrayList<>(mine.links());
        Set<String> urls = new HashSet<>();
        mine.links().forEach(l -> urls.add(normalize(l.url())));
        theirs.links().stream().filter(l -> urls.add(normalize(l.url()))).forEach(links::add);
        return new PersonalInfo(
                fill(mine.name(), theirs.name()),
                fill(mine.headline(), theirs.headline()),
                fill(mine.summary(), theirs.summary()),
                mine.location() != null ? mine.location() : theirs.location(),
                mine.email(),
                links);
    }

    private static List<Experience> experience(List<Experience> mine, List<Experience> theirs) {
        if (theirs.isEmpty()) {
            return mine;
        }
        List<Experience> pending = new ArrayList<>(mine);
        List<Experience> result = new ArrayList<>();
        for (Experience job : theirs) {
            Optional<Experience> match = pending.stream()
                    .filter(m -> !isTodo(m.company()) && m.company().equalsIgnoreCase(job.company()))
                    .findFirst()
                    .or(() -> job.current()
                            ? pending.stream().filter(m -> isTodo(m.company()) && m.current()).findFirst()
                            : Optional.empty());
            match.ifPresent(pending::remove);
            result.add(match.map(m -> inherit(job, m)).orElse(job));
        }
        return result;
    }

    /** Imported job + what only the hand-written one knows (evidence links, technologies, real highlights). */
    private static Experience inherit(Experience imported, Experience mine) {
        List<String> highlights = imported.highlights().stream().allMatch(ProfileMerge::isTodo)
                ? mine.highlights().stream().filter(h -> !isTodo(h)).toList()
                : imported.highlights();
        if (highlights.isEmpty()) {
            highlights = imported.highlights();
        }
        List<String> technologies = new ArrayList<>(mine.technologies());
        imported.technologies().stream().filter(t -> !technologies.contains(t)).forEach(technologies::add);
        return new Experience(imported.id(), imported.role(), imported.company(),
                imported.location() != null ? imported.location() : mine.location(),
                imported.period(), highlights, technologies, mine.evidenceIds());
    }

    private static List<Project> projects(List<Project> mine, List<Project> theirs) {
        List<Project> result = new ArrayList<>(mine);
        Set<String> keys = new HashSet<>();
        mine.forEach(p -> {
            keys.add(p.id());
            keys.add(p.name().toLowerCase(Locale.ROOT));
        });
        for (Project p : theirs) {
            if (keys.add(p.id()) && keys.add(p.name().toLowerCase(Locale.ROOT))) {
                result.add(p);
            }
        }
        return result;
    }

    private static List<Skill> skills(List<Skill> mine, List<Skill> theirs) {
        Map<String, Skill> byName = new LinkedHashMap<>();
        mine.forEach(s -> byName.put(s.name().toLowerCase(Locale.ROOT), s));
        theirs.forEach(s -> byName.putIfAbsent(s.name().toLowerCase(Locale.ROOT), s));
        return List.copyOf(byName.values());
    }

    private static String fill(String mine, String theirs) {
        boolean replaceable = mine == null || isTodo(mine);
        return replaceable && theirs != null && !isTodo(theirs) ? theirs : mine;
    }

    private static boolean isTodo(String value) {
        return value != null && value.contains(TodoCounter.MARKER);
    }

    private static String normalize(String url) {
        return url.toLowerCase(Locale.ROOT).replaceFirst("^http://", "https://").replaceAll("/+$", "");
    }
}
