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

    /**
     * @param notInImport hand-written jobs the import has no match for. The platform owns the career, so they are not
     *                    in the result, but the caller must tell the user: they may carry evidence links.
     */
    public record Result(Profile profile, List<Experience> notInImport) {
        public Result {
            notInImport = List.copyOf(notInImport);
        }
    }

    public static Profile careerFrom(Profile existing, Profile imported) {
        return merge(existing, imported).profile();
    }

    public static Result merge(Profile existing, Profile imported) {
        List<Experience> notInImport = new ArrayList<>();
        Profile profile = new Profile(existing.language(), person(existing.person(), imported.person()),
                experience(existing.experience(), imported.experience(), notInImport),
                imported.education().isEmpty() ? existing.education() : imported.education(),
                projects(existing.projects(), imported.projects()),
                skills(existing.skills(), imported.skills()),
                existing.evidence(), existing.labs(),
                imported.languages().isEmpty() ? existing.languages() : imported.languages(),
                imported.certifications().isEmpty() ? existing.certifications() : imported.certifications());
        return new Result(profile, notInImport);
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

    private static List<Experience> experience(List<Experience> mine, List<Experience> theirs,
                                               List<Experience> notInImport) {
        if (theirs.isEmpty()) {
            return mine;
        }
        List<Experience> pending = new ArrayList<>(mine);
        List<Experience> result = new ArrayList<>();
        for (Experience job : theirs) {
            Optional<Experience> match = pending.stream()
                    .filter(m -> !isTodo(m.company()) && company(m.company()).equals(company(job.company())))
                    .findFirst()
                    .or(() -> job.current()
                            ? pending.stream().filter(m -> isTodo(m.company()) && m.current()).findFirst()
                            : Optional.empty());
            match.ifPresent(pending::remove);
            result.add(match.map(m -> inherit(job, m)).orElse(job));
        }
        notInImport.addAll(pending);
        return result;
    }

    private static final Set<String> LEGAL_FORMS = Set.of("sl", "slu", "sa", "sau", "sll", "scoop", "sc", "cb",
            "inc", "incorporated", "ltd", "limited", "llc", "llp", "plc", "corp", "corporation", "co", "company",
            "gmbh", "ag", "kg", "bv", "nv", "sas", "sarl", "srl", "spa", "ab", "as", "oy", "group");

    /** "Example Corporation, S.L." and "Example Corp" are the same employer: no accents, punctuation or legal form. */
    static String company(String name) {
        String ascii = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        List<String> words = new ArrayList<>(List.of(ascii.toLowerCase(Locale.ROOT)
                .replaceAll("\\b([a-z])\\.(?=[a-z]\\b)", "$1")   // s.l. -> sl, s.a. -> sa
                .replaceAll("[^a-z0-9]+", " ").strip().split(" ")));
        while (words.size() > 1 && LEGAL_FORMS.contains(words.getLast())) {
            words.removeLast();
        }
        return String.join(" ", words);
    }

    /**
     * Imported job + what only the hand-written one knows (evidence links, technologies, real highlights, and its
     * period when the imported one is flagged as unknown).
     */
    private static Experience inherit(Experience imported, Experience mine) {
        boolean unknownDates = imported.highlights().stream().anyMatch(h -> h.startsWith(TodoCounter.DATES))
                && mine.highlights().stream().noneMatch(h -> h.startsWith(TodoCounter.DATES));
        Period period = unknownDates ? mine.period() : imported.period();
        List<String> importedHighlights = unknownDates
                ? imported.highlights().stream().filter(h -> !h.startsWith(TodoCounter.DATES)).toList()
                : imported.highlights();
        List<String> highlights = importedHighlights.stream().allMatch(ProfileMerge::isTodo)
                ? mine.highlights().stream().filter(h -> !isTodo(h)).toList()
                : importedHighlights;
        if (highlights.isEmpty()) {
            highlights = importedHighlights;
        }
        List<String> technologies = new ArrayList<>(mine.technologies());
        imported.technologies().stream().filter(t -> !technologies.contains(t)).forEach(technologies::add);
        return new Experience(imported.id(), imported.role(), imported.company(),
                imported.location() != null ? imported.location() : mine.location(),
                period, highlights, technologies, mine.evidenceIds());
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
