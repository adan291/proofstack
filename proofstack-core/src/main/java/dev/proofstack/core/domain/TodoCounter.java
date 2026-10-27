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
        profile.experience().forEach(e -> {
            add(texts, e.role(), e.company(), e.location());
            texts.addAll(e.highlights());
        });
        profile.education().forEach(e -> {
            add(texts, e.title(), e.institution());
            texts.addAll(e.highlights());
        });
        profile.projects().forEach(pr -> {
            add(texts, pr.name(), pr.summary(), pr.problem(), pr.solution());
            texts.addAll(pr.learnings());
        });
        profile.evidence().forEach(e -> add(texts, e.title(), e.context(), e.action(), e.result()));
        profile.labs().forEach(l -> add(texts, l.name(), l.description()));
        return (int) texts.stream().filter(Objects::nonNull).filter(t -> t.contains(MARKER)).count();
    }

    private static void add(List<String> texts, String... values) {
        for (String v : values) {
            texts.add(v);
        }
    }
}
