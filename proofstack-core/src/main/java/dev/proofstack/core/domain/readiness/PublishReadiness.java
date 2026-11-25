package dev.proofstack.core.domain.readiness;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import dev.proofstack.core.domain.Certification;
import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileTexts;
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

        // Placeholders: every published text, from the one list that knows them all.
        for (ProfileTexts.Text t : ProfileTexts.of(profile)) {
            if (TodoCounter.isPlaceholder(t.value())) {
                findings.add(new Finding(Severity.ERROR, t.path(),
                        "placeholder still present: \"" + abbreviate(t.value()) + "\"",
                        "replace it with real information or remove the field"));
            }
        }

        links(findings, "$.person.links", p.links());
        if (p.email() != null && isPlaceholderUrl("mailto:" + p.email())) {
            findings.add(new Finding(Severity.ERROR, "$.person.email", "sample email address: " + p.email(),
                    "use your real address or remove the field"));
        }
        if (p.summary() == null) {
            findings.add(warn("$.person.summary", "no summary",
                    "2-3 sentences: what you build, for whom, and what you are exploring now"));
        }
        if (p.links().isEmpty() && p.email() == null) {
            findings.add(warn("$.person.links", "no way to contact you", "add at least LinkedIn or GitHub"));
        }

        if (profile.experience().isEmpty()) {
            findings.add(warn("$.experience", "no experience listed", "even one entry gives context to your projects"));
        }
        for (int i = 0; i < profile.experience().size(); i++) {
            if (profile.experience().get(i).highlights().isEmpty()) {
                findings.add(warn("$.experience[" + i + "].highlights", "no highlights",
                        "1-3 concrete achievements, no buzzwords"));
            }
        }

        if (profile.skills().isEmpty()) {
            findings.add(warn("$.skills", "no skills", "skills place you on the architecture map"));
        }
        for (int i = 0; i < profile.certifications().size(); i++) {
            Certification c = profile.certifications().get(i);
            if (c.link() != null) {
                links(findings, "$.certifications[" + i + "].link", List.of(c.link()));
            }
        }

        for (int i = 0; i < profile.projects().size(); i++) {
            Project pr = profile.projects().get(i);
            String path = "$.projects[" + i + "]";
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
            if (e.link() != null) {
                links(findings, path + ".link", List.of(e.link()));
            }
            if (!referenced.contains(e.id())) {
                findings.add(warn(path, "'" + e.id() + "' is not linked to any experience or project",
                        "add its id to the 'evidence' list of the job or project it proves"));
            }
        }

        // Errors first, then by path, so the most important problems are always on top.
        findings.sort((a, b) -> a.severity() != b.severity()
                ? a.severity().compareTo(b.severity()) : a.path().compareTo(b.path()));
        return List.copyOf(findings);
    }

    public static boolean hasErrors(List<Finding> findings) {
        return findings.stream().anyMatch(f -> f.severity() == Severity.ERROR);
    }

    private static void links(List<Finding> findings, String path, List<Link> links) {
        for (int i = 0; i < links.size(); i++) {
            Link link = links.get(i);
            String at = links.size() == 1 && path.endsWith(".link") ? path : path + "[" + i + "]";
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
        return oneLine.length() > 60 ? oneLine.substring(0, 57) + "..." : oneLine;
    }
}
