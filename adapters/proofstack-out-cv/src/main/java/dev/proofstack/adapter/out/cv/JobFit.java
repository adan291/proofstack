package dev.proofstack.adapter.out.cv;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import dev.proofstack.core.domain.Profile;

/**
 * How a profile lines up with a job posting.
 *
 * @param matched terms the posting uses and the profile also uses, most frequent first
 * @param gaps    terms the posting stresses (twice or more, or technical on their face) that the profile never
 *                mentions. A heuristic to review by hand: it can include ordinary words, and a gap is only a real
 *                gap if you actually have the skill.
 */
public record JobFit(List<String> matched, List<String> gaps) {

    private static final int MAX_LISTED = 20;
    private static final int MIN_REPEATS = 2;

    public static JobFit of(Profile profile, JobOffer offer) {
        Set<String> vocabulary = vocabulary(profile);
        Comparator<Map.Entry<String, Integer>> byFrequency =
                Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey());
        List<String> matched = offer.terms().entrySet().stream()
                .filter(e -> vocabulary.contains(e.getKey()))
                .sorted(byFrequency).map(Map.Entry::getKey).limit(MAX_LISTED).toList();
        List<String> gaps = offer.terms().entrySet().stream()
                .filter(e -> !vocabulary.contains(e.getKey()))
                .filter(e -> e.getValue() >= MIN_REPEATS || JobOffer.looksTechnical(e.getKey()))
                .sorted(byFrequency).map(Map.Entry::getKey).limit(MAX_LISTED).toList();
        return new JobFit(matched, gaps);
    }

    /** Every normalised word the profile says about its skills, jobs, projects and evidence. */
    static Set<String> vocabulary(Profile profile) {
        Set<String> words = new HashSet<>();
        profile.skills().forEach(s -> words.addAll(JobOffer.tokens(s.name())));
        profile.experience().forEach(e -> {
            words.addAll(JobOffer.tokens(e.role()));
            e.highlights().forEach(h -> words.addAll(JobOffer.tokens(h)));
            e.technologies().forEach(t -> words.addAll(JobOffer.tokens(t)));
        });
        profile.projects().forEach(p -> words.addAll(projectWords(p)));
        profile.certifications().forEach(c -> words.addAll(JobOffer.tokens(c.title())));
        return words;
    }

    static Set<String> projectWords(dev.proofstack.core.domain.Project p) {
        Set<String> words = new HashSet<>();
        Stream.of(p.name(), p.summary(), p.problem(), p.solution())
                .filter(s -> s != null)
                .forEach(s -> words.addAll(JobOffer.tokens(s)));
        Stream.concat(p.techStack().stream(), p.learnings().stream()).forEach(s -> words.addAll(JobOffer.tokens(s)));
        return words;
    }
}
