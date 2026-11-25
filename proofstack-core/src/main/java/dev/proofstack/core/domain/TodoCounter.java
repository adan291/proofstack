package dev.proofstack.core.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Counts placeholder values ({@value #MARKER}) left in a profile, e.g. after an import. */
public final class TodoCounter {

    public static final String MARKER = "TODO";

    private TodoCounter() {
    }

    public static int count(Profile profile) {
        List<String> texts = new ArrayList<>();
        PersonalInfo p = profile.person();
        add(texts, p.name(), p.headline(), p.summary(), p.location(), p.email());
        p.links().forEach(l -> add(texts, l.label(), l.url()));
        profile.skills().forEach(s -> add(texts, s.name()));
        profile.experience().forEach(e -> {
            add(texts, e.role(), e.company(), e.location());
            texts.addAll(e.highlights());
            texts.addAll(e.technologies());
        });
        profile.education().forEach(e -> {
            add(texts, e.title(), e.institution());
            texts.addAll(e.highlights());
        });
        profile.projects().forEach(pr -> {
            add(texts, pr.name(), pr.summary(), pr.problem(), pr.solution());
            texts.addAll(pr.techStack());
            texts.addAll(pr.learnings());
            pr.links().forEach(l -> add(texts, l.label(), l.url()));
        });
        profile.evidence().forEach(e -> {
            add(texts, e.title(), e.context(), e.action(), e.result());
            if (e.link() != null) {
                add(texts, e.link().label(), e.link().url());
            }
        });
        profile.labs().forEach(l -> add(texts, l.name(), l.description()));
        profile.languages().forEach(l -> add(texts, l.name(), l.level()));
        profile.certifications().forEach(c -> {
            add(texts, c.title(), c.issuer());
            if (c.link() != null) {
                add(texts, c.link().label(), c.link().url());
            }
        });
        return (int) texts.stream().filter(Objects::nonNull).filter(t -> t.contains(MARKER)).count();
    }

    private static void add(List<String> texts, String... values) {
        for (String v : values) {
            texts.add(v);
        }
    }
}
