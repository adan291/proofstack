package dev.proofstack.core.domain.readiness;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import dev.proofstack.core.domain.Certification;
import dev.proofstack.core.domain.Education;
import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.Lab;
import dev.proofstack.core.domain.Language;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.TodoCounter;

/**
 * "Is this profile ready to be public?" A valid profile can still be embarrassing to publish:
 * placeholders, sample links, projects that list technologies but never say what problem they solved.
 */
public final class PublishReadiness {

    public enum Severity { ERROR, WARNING }

    /** One problem, located with the same JSONPath-like syntax as validation errors. */
    public record Finding(Severity severity, String path, String message, String hint) {
        public Finding {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(message, "message");
            hint = hint == null ? "" : hint;
        }
    }

    private static final Set<String> SAMPLE_HOSTS = Set.of("example.com", "example.org", "example.net", "example.dev");

    private PublishReadiness() {
    }

    public static List<Finding> check(Profile profile) {
        List<Finding> findings = new ArrayList<>();
        PersonalInfo p = profile.person();

        text(findings, "$.person.name", p.name());
        text(findings, "$.person.headline", p.headline());
        text(findings, "$.person.summary", p.summary());
        text(findings, "$.person.location", p.location());
        links(findings, "$.person.links", p.links());
        if (p.email() != null && isPlaceholderUrl("mailto:" + p.email())) {
            findings.add(new Finding(Severity.ERROR, "$.person.email", "sample email address: " + p.email(),
                    "use your real address or remove the field"));
        }
        if (p.summary() == null) {
            findings.add(warn("$.person.summary", "no summary",
                    "2–3 sentences: what you build, for whom, and what you are exploring now"));
        }
        if (p.links().isEmpty() && p.email() == null) {
            findings.add(warn("$.person.links", "no way to contact you", "add at least LinkedIn or GitHub"));
        }

        if (profile.experience().isEmpty()) {
            findings.add(warn("$.experience", "no experience listed", "even one entry gives context to your projects"));
        }
        for (int i = 0; i < profile.experience().size(); i++) {
            Experience e = profile.experience().get(i);
            String path = "$.experience[" + i + "]";
            text(findings, path + ".role", e.role());
            text(findings, path + ".company", e.company());
            text(findings, path + ".location", e.location());
            texts(findings, path + ".highlights", e.highlights());
            texts(findings, path + ".technologies", e.technologies());
            if (e.highlights().isEmpty()) {
                findings.add(warn(path + ".highlights", "no highlights", "1–3 concrete achievements, no buzzwords"));
            }
        }

        for (int i = 0; i < profile.education().size(); i++) {
            Education e = profile.education().get(i);
            String path = "$.education[" + i + "]";
            text(findings, path + ".title", e.title());
            text(findings, path + ".institution", e.institution());
            texts(findings, path + ".highlights", e.highlights());
        }

        if (profile.skills().isEmpty()) {
            findings.add(warn("$.skills", "no skills", "skills place you on the architecture map"));
        }
        for (int i = 0; i < profile.skills().size(); i++) {
            text(findings, "$.skills[" + i + "].name", profile.skills().get(i).name());
        }
        for (int i = 0; i < profile.languages().size(); i++) {
            Language l = profile.languages().get(i);
            text(findings, "$.languages[" + i + "].name", l.name());
            text(findings, "$.languages[" + i + "].level", l.level());
        }
        for (int i = 0; i < profile.certifications().size(); i++) {
            Certification c = profile.certifications().get(i);
            String path = "$.certifications[" + i + "]";
            text(findings, path + ".title", c.title());
            text(findings, path + ".issuer", c.issuer());
            if (c.link() != null) {
                links(findings, path + ".link", List.of(c.link()));
            }
        }

        for (int i = 0; i < profile.projects().size(); i++) {
            Project pr = profile.projects().get(i);
            String path = "$.projects[" + i + "]";
            text(findings, path + ".name", pr.name());
            text(findings, path + ".summary", pr.summary());
            text(findings, path + ".problem", pr.problem());
            text(findings, path + ".solution", pr.solution());
            texts(findings, path + ".techStack", pr.techStack());
            texts(findings, path + ".learnings", pr.learnings());
            links(findings, path + ".links", pr.links());
            if (pr.problem() == null || pr.solution() == null) {
                findings.add(warn(path, "'" + pr.id() + "' does not say which problem it solved or how",
                        "problem + solution is what turns a project into evidence"));
            }
            if (pr.links().isEmpty()) {
                findings.add(warn(path + ".links", "'" + pr.id() + "' has no link",
                        "link the repo, a demo, a post or a video so it can be verified"));
            }
        }

        Set<String> referenced = new HashSet<>();
        profile.experience().forEach(e -> referenced.addAll(e.evidenceIds()));
        profile.projects().forEach(pr -> referenced.addAll(pr.evidenceIds()));
        for (int i = 0; i < profile.evidence().size(); i++) {
            Evidence e = profile.evidence().get(i);
            String path = "$.evidence[" + i + "]";
            text(findings, path + ".title", e.title());
            text(findings, path + ".context", e.context());
            text(findings, path + ".action", e.action());
            text(findings, path + ".result", e.result());
            if (e.link() != null) {
                links(findings, path + ".link", List.of(e.link()));
            }
            if (!referenced.contains(e.id())) {
                findings.add(warn(path, "'" + e.id() + "' is not linked to any experience or project",
                        "add its id to the 'evidence' list of the job or project it proves"));
            }
        }

        for (int i = 0; i < profile.labs().size(); i++) {
            Lab l = profile.labs().get(i);
            text(findings, "$.labs[" + i + "].name", l.name());
            text(findings, "$.labs[" + i + "].description", l.description());
        }

        // Errors first, then by path, so the most important problems are always on top.
        findings.sort((a, b) -> a.severity() != b.severity()
                ? a.severity().compareTo(b.severity()) : a.path().compareTo(b.path()));
        return List.copyOf(findings);
    }

    public static boolean hasErrors(List<Finding> findings) {
        return findings.stream().anyMatch(f -> f.severity() == Severity.ERROR);
    }

    private static void text(List<Finding> findings, String path, String value) {
        if (value != null && value.contains(TodoCounter.MARKER)) {
            findings.add(new Finding(Severity.ERROR, path, "placeholder still present: \"" + abbreviate(value) + "\"",
                    "replace it with real information or remove the field"));
        }
    }

    private static void texts(List<Finding> findings, String path, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            text(findings, path + "[" + i + "]", values.get(i));
        }
    }

    private static void links(List<Finding> findings, String path, List<Link> links) {
        for (int i = 0; i < links.size(); i++) {
            Link link = links.get(i);
            String at = links.size() == 1 && path.endsWith(".link") ? path : path + "[" + i + "]";
            text(findings, at + ".label", link.label());
            if (isPlaceholderUrl(link.url())) {
                findings.add(new Finding(Severity.ERROR, at + ".url", "sample or generic URL: " + link.url(),
                        "point it to your own profile, repository or demo"));
            }
        }
    }

    static boolean isPlaceholderUrl(String url) {
        if (url.toLowerCase(Locale.ROOT).startsWith("mailto:")) {
            String domain = url.substring(url.indexOf('@') + 1).toLowerCase(Locale.ROOT);
            return SAMPLE_HOSTS.contains(domain);
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            String path = uri.getPath() == null ? "" : uri.getPath();
            boolean bareSite = path.isEmpty() || path.equals("/") || path.equals("/in/") || path.equals("/in");
            return SAMPLE_HOSTS.contains(host) || host.endsWith(".example.com")
                    || (bareSite && (host.equals("github.com") || host.equals("linkedin.com")));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Finding warn(String path, String message, String hint) {
        return new Finding(Severity.WARNING, path, message, hint);
    }

    private static String abbreviate(String value) {
        String oneLine = value.replaceAll("\\s+", " ").strip();
        return oneLine.length() > 60 ? oneLine.substring(0, 57) + "…" : oneLine;
    }
}
