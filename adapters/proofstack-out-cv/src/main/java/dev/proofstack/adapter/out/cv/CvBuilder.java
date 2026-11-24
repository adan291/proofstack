package dev.proofstack.adapter.out.cv;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import dev.proofstack.adapter.out.cv.Cv.Cert;
import dev.proofstack.adapter.out.cv.Cv.Contact;
import dev.proofstack.adapter.out.cv.Cv.Job;
import dev.proofstack.adapter.out.cv.Cv.Lang;
import dev.proofstack.adapter.out.cv.Cv.Proj;
import dev.proofstack.adapter.out.cv.Cv.SkillGroup;
import dev.proofstack.adapter.out.cv.Cv.Study;
import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.Period;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.Skill;

/**
 * Picks and orders what goes on the CV. Pure function of the profile: no generated text, so the CV can only say
 * what the profile already proves. A text that still contains the profile's placeholder marker is left out, never printed.
 */
final class CvBuilder {

    private static final String PLACEHOLDER = "TODO";
    private static final int MAX_STACK = 8;
    private static final int MAX_JOB_BONUS = 6;

    private CvBuilder() {
    }

    static Cv build(Profile profile, CvOptions options, CvText t) {
        var person = profile.person();
        return new Cv(t, clean(person.name()), clean(person.headline()), contact(profile),
                clean(person.summary()), skills(profile, options, t), jobs(profile, t), projects(profile, options),
                studies(profile, t), certifications(profile, t), languages(profile));
    }

    private static List<Contact> contact(Profile profile) {
        var person = profile.person();
        List<Contact> contact = new ArrayList<>();
        if (!clean(person.location()).isEmpty()) {
            contact.add(new Contact(clean(person.location()), ""));
        }
        if (!clean(person.email()).isEmpty()) {
            contact.add(new Contact(person.email().strip(), "mailto:" + person.email().strip()));
        }
        person.links().forEach(link -> {
            if (!clean(link.label()).isEmpty()) {
                contact.add(new Contact(clean(link.label()), link.url()));
            }
        });
        return contact;
    }

    private static List<SkillGroup> skills(Profile profile, CvOptions options, CvText t) {
        Map<Area, List<String>> byArea = new EnumMap<>(Area.class);
        for (Skill skill : profile.skills()) {
            if (!clean(skill.name()).isEmpty()) {
                byArea.computeIfAbsent(skill.area(), a -> new ArrayList<>()).add(clean(skill.name()));
            }
        }
        List<Area> order = new ArrayList<>(List.of(Area.values()));
        if (options.focus() != null) {
            order.remove(options.focus());
            order.addFirst(options.focus());
        }
        Set<String> wanted = options.job() == null ? Set.of() : options.job().terms().keySet();
        if (!wanted.isEmpty()) {
            byArea.replaceAll((area, names) -> names.stream()
                    .sorted(Comparator.comparingInt((String n) -> matches(n, wanted) ? 0 : 1)).toList());
            List<Area> rest = order.stream().filter(a -> a != options.focus()).sorted(Comparator
                    .comparingLong((Area a) -> -byArea.getOrDefault(a, List.of()).stream().filter(n -> matches(n, wanted)).count())).toList();
            order = new ArrayList<>(rest);
            if (options.focus() != null) {
                order.addFirst(options.focus());
            }
        }
        return order.stream()
                .filter(byArea::containsKey)
                .map(area -> new SkillGroup(t.area(area), String.join(", ", byArea.get(area))))
                .toList();
    }

    private static boolean matches(String skill, Set<String> wanted) {
        return JobOffer.tokens(skill).stream().anyMatch(wanted::contains);
    }

    private static List<Job> jobs(Profile profile, CvText t) {
        return profile.experience().stream()
                .sorted(Comparator.comparing((dev.proofstack.core.domain.Experience e) -> e.period().start()).reversed())
                .map(e -> new Job(clean(e.role()), clean(e.company()), clean(e.location()), period(e.period(), t),
                        usable(e.highlights()), String.join(", ", usable(e.technologies()))))
                .toList();
    }

    private static List<Study> studies(Profile profile, CvText t) {
        return profile.education().stream()
                .sorted(Comparator.comparing((dev.proofstack.core.domain.Education e) -> e.period().start()).reversed())
                .map(e -> new Study(clean(e.title()), clean(e.institution()), period(e.period(), t),
                        usable(e.highlights())))
                .toList();
    }

    private static List<Cert> certifications(Profile profile, CvText t) {
        DateTimeFormatter month = DateTimeFormatter.ofPattern("MMM yyyy", t.locale());
        return profile.certifications().stream()
                .filter(c -> !clean(c.title()).isEmpty())
                .sorted(Comparator.comparing((dev.proofstack.core.domain.Certification c) -> c.issued() == null
                        ? java.time.YearMonth.of(1900, 1) : c.issued()).reversed())
                .map(c -> new Cert(clean(c.title()),
                        String.join(" · ", java.util.stream.Stream.of(clean(c.issuer()),
                                c.issued() == null ? "" : month.format(c.issued())).filter(s -> !s.isEmpty()).toList()),
                        c.link() == null ? "" : c.link().url()))
                .toList();
    }

    private static List<Lang> languages(Profile profile) {
        return profile.languages().stream()
                .filter(l -> !clean(l.name()).isEmpty())
                .map(l -> new Lang(clean(l.name()), clean(l.level())))
                .toList();
    }

    private static List<Proj> projects(Profile profile, CvOptions options) {
        List<Project> candidates = profile.projects().stream()
                .filter(p -> !clean(p.name()).isEmpty() && !clean(p.summary()).isEmpty())
                .toList();
        // Stable sort: ties keep the order the author chose in the profile.
        return IntStream.range(0, candidates.size()).boxed()
                .sorted(Comparator.comparingInt((Integer i) -> -score(candidates.get(i), profile, options)))
                .limit(options.maxProjects())
                .map(candidates::get)
                .map(p -> toProj(p, profile))
                .toList();
    }

    private static int score(Project p, Profile profile, CvOptions options) {
        int score = 0;
        if (options.focus() != null && p.areas().contains(options.focus())) {
            score += 4;
        }
        if (!clean(p.problem()).isEmpty() && !clean(p.solution()).isEmpty()) {
            score += 2;
        }
        if (!result(p, profile).isEmpty()) {
            score += 2;
        }
        if (!p.links().isEmpty()) {
            score += 1;
        }
        if (options.job() != null) {
            Set<String> words = JobFit.projectWords(p);
            score += (int) Math.min(MAX_JOB_BONUS, options.job().terms().keySet().stream().filter(words::contains).count());
        }
        return score;
    }

    private static Proj toProj(Project p, Profile profile) {
        var link = p.links().stream().findFirst();
        return new Proj(clean(p.name()), clean(p.summary()),
                String.join(", ", usable(p.techStack()).stream().limit(MAX_STACK).toList()),
                result(p, profile),
                link.map(l -> clean(l.label())).orElse(""),
                link.map(l -> l.url()).orElse(""));
    }

    /** The first usable result among the evidence that backs the project. */
    private static String result(Project p, Profile profile) {
        return p.evidenceIds().stream()
                .map(profile::evidence)
                .flatMap(Optional::stream)
                .map(Evidence::result)
                .map(CvBuilder::clean)
                .filter(r -> !r.isEmpty())
                .findFirst()
                .orElse("");
    }

    static String period(Period period, CvText t) {
        DateTimeFormatter month = DateTimeFormatter.ofPattern("MMM yyyy", t.locale());
        return month.format(period.start()) + " – " + period.endDate().map(month::format).orElse(t.present());
    }

    private static List<String> usable(List<String> texts) {
        return texts.stream().map(CvBuilder::clean).filter(s -> !s.isEmpty()).toList();
    }

    /** One line, trimmed; empty when missing or still a placeholder. */
    static String clean(String text) {
        if (text == null || text.contains(PLACEHOLDER)) {
            return "";
        }
        return text.strip().replaceAll("\\s+", " ");
    }
}
