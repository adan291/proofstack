package dev.proofstack.core.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Every text of a profile that can end up published, with its location ({@code $.projects[0].links[1].url}). The one
 * place that knows which fields exist: placeholder checks and counters walk this list, so they cannot drift apart.
 */
public final class ProfileTexts {

    /** A text and where it is, in the same JSONPath-like syntax as validation errors. */
    public record Text(String path, String value) {
    }

    private ProfileTexts() {
    }

    public static List<Text> of(Profile profile) {
        List<Text> out = new ArrayList<>();
        PersonalInfo p = profile.person();
        add(out, "$.person.name", p.name());
        add(out, "$.person.headline", p.headline());
        add(out, "$.person.summary", p.summary());
        add(out, "$.person.location", p.location());
        add(out, "$.person.email", p.email());
        links(out, "$.person.links", p.links());
        each(profile.skills(), (i, s) -> add(out, "$.skills[" + i + "].name", s.name()));
        each(profile.experience(), (i, e) -> {
            String at = "$.experience[" + i + "]";
            add(out, at + ".role", e.role());
            add(out, at + ".company", e.company());
            add(out, at + ".location", e.location());
            all(out, at + ".highlights", e.highlights());
            all(out, at + ".technologies", e.technologies());
        });
        each(profile.education(), (i, e) -> {
            String at = "$.education[" + i + "]";
            add(out, at + ".title", e.title());
            add(out, at + ".institution", e.institution());
            all(out, at + ".highlights", e.highlights());
        });
        each(profile.projects(), (i, pr) -> {
            String at = "$.projects[" + i + "]";
            add(out, at + ".name", pr.name());
            add(out, at + ".summary", pr.summary());
            add(out, at + ".problem", pr.problem());
            add(out, at + ".solution", pr.solution());
            all(out, at + ".techStack", pr.techStack());
            all(out, at + ".learnings", pr.learnings());
            links(out, at + ".links", pr.links());
        });
        each(profile.evidence(), (i, e) -> {
            String at = "$.evidence[" + i + "]";
            add(out, at + ".title", e.title());
            add(out, at + ".context", e.context());
            add(out, at + ".action", e.action());
            add(out, at + ".result", e.result());
            link(out, at + ".link", e.link());
        });
        each(profile.labs(), (i, l) -> {
            add(out, "$.labs[" + i + "].name", l.name());
            add(out, "$.labs[" + i + "].description", l.description());
        });
        each(profile.languages(), (i, l) -> {
            add(out, "$.languages[" + i + "].name", l.name());
            add(out, "$.languages[" + i + "].level", l.level());
        });
        each(profile.certifications(), (i, c) -> {
            String at = "$.certifications[" + i + "]";
            add(out, at + ".title", c.title());
            add(out, at + ".issuer", c.issuer());
            link(out, at + ".link", c.link());
        });
        return List.copyOf(out);
    }

    private interface Indexed<T> {
        void accept(int index, T item);
    }

    private static <T> void each(List<T> items, Indexed<T> action) {
        for (int i = 0; i < items.size(); i++) {
            action.accept(i, items.get(i));
        }
    }

    private static void links(List<Text> out, String path, List<Link> links) {
        each(links, (i, l) -> link(out, path + "[" + i + "]", l));
    }

    private static void link(List<Text> out, String path, Link link) {
        if (link != null) {
            add(out, path + ".label", link.label());
            add(out, path + ".url", link.url());
        }
    }

    private static void all(List<Text> out, String path, List<String> values) {
        each(values, (i, v) -> add(out, path + "[" + i + "]", v));
    }

    private static void add(List<Text> out, String path, String value) {
        if (value != null) {
            out.add(new Text(path, value));
        }
    }
}
