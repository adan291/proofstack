package dev.proofstack.adapter.out.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import dev.proofstack.adapter.out.web.PortfolioView.EducationView;
import dev.proofstack.adapter.out.web.PortfolioView.EvidenceView;
import dev.proofstack.adapter.out.web.PortfolioView.ExperienceView;
import dev.proofstack.adapter.out.web.PortfolioView.LabView;
import dev.proofstack.adapter.out.web.PortfolioView.LinkView;
import dev.proofstack.adapter.out.web.PortfolioView.NodeView;
import dev.proofstack.adapter.out.web.PortfolioView.PersonView;
import dev.proofstack.adapter.out.web.PortfolioView.ProjectView;
import dev.proofstack.core.domain.ArchitectureMap;
import dev.proofstack.core.domain.AreaNode;
import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.Period;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.Skill;
import dev.proofstack.core.port.out.Artifact;
import dev.proofstack.core.port.out.PortfolioRenderer;

/**
 * Driven adapter: renders the "architecture" theme, a career shown as a system diagram.
 * Output is fully static: {@code index.html}, {@code styles.css}, {@code app.js}.
 */
public final class ArchitectureThemeRenderer implements PortfolioRenderer {

    private static final String THEME = "/themes/architecture/";
    private static final int FEATURED = 3;

    private static final String BASE_CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; base-uri 'none'; form-action 'none'";

    private final Template index;
    private final String styles;
    private final String script;
    private final URI assistantEndpoint;
    private final String cvHref;
    private final Map<String, String> alternates;

    public ArchitectureThemeRenderer() {
        this(null);
    }

    /**
     * @param assistantBaseUrl base URL of the optional ask service (ADR-007), or {@code null} for none.
     *                         Must be https; plain http is only accepted for localhost development.
     */
    public ArchitectureThemeRenderer(URI assistantBaseUrl) {
        this(assistantBaseUrl, null);
    }

    /**
     * @param assistantBaseUrl see {@link #ArchitectureThemeRenderer(URI)}
     * @param cvHref           relative path of the CV published with the site (e.g. {@code cv/cv.pdf}), or {@code null}
     */
    public ArchitectureThemeRenderer(URI assistantBaseUrl, String cvHref) {
        this(assistantBaseUrl, cvHref, Map.of());
    }

    /**
     * @param alternates same portfolio in other languages: language tag (e.g. {@code en}) to a relative link
     *                   (e.g. {@code en/} or {@code ../}). Shown as a language switch and as {@code hreflang} links.
     */
    public ArchitectureThemeRenderer(URI assistantBaseUrl, String cvHref, Map<String, String> alternates) {
        this.alternates = validAlternates(alternates);
        this.assistantEndpoint = assistantBaseUrl == null ? null : askEndpoint(assistantBaseUrl);
        this.cvHref = cvHref == null ? null : relativeHref(cvHref);
        // escapeHTML(true) is the default; set explicitly because it is a security property of this adapter.
        Mustache.Compiler compiler = Mustache.compiler().escapeHTML(true).nullValue("");
        try (Reader reader = new InputStreamReader(resource("index.mustache"), StandardCharsets.UTF_8)) {
            this.index = compiler.compile(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.styles = text("styles.css");
        this.script = text("app.js");
    }

    @Override
    public List<Artifact> render(Profile profile) {
        PortfolioView view = toView(profile);
        return List.of(
                new Artifact("index.html", index.execute(view)),
                new Artifact("styles.css", styles),
                new Artifact("app.js", script));
    }

    PortfolioView toView(Profile profile) {
        UiText t = UiText.forLanguage(profile.language());
        var p = profile.person();
        LinkView cv = cvHref == null ? null : new LinkView(t.downloadCv(), cvHref);
        PersonView person = new PersonView(p.name(), initials(p.name()), p.headline(), p.summary(), p.location(),
                p.email(), links(p.links()), cv, p.phone(), p.phone() == null ? null : "tel:" + p.phone().replaceAll("[^+0-9]", ""),
                p.contactMessage());

        List<NodeView> nodes = ArchitectureMap.from(profile).nodes().stream()
                .map(node -> node(node, profile, t))
                .toList();

        List<ExperienceView> experience = profile.experience().stream()
                .map(e -> new ExperienceView(e.role(), e.company(), e.location(), period(e.period(), t),
                        e.highlights(), e.technologies()))
                .toList();

        List<EducationView> education = profile.education().stream()
                .map(e -> new EducationView(e.title(), e.institution(), period(e.period(), t), e.highlights()))
                .toList();

        PortfolioView.AssistantView assistant = assistantEndpoint == null ? null
                : new PortfolioView.AssistantView(assistantEndpoint.toString());
        String csp = assistantEndpoint == null ? BASE_CSP : BASE_CSP + "; connect-src " + origin(assistantEndpoint);
        List<PortfolioView.AlternateView> languages = alternates.entrySet().stream()
                .map(e -> new PortfolioView.AlternateView(e.getKey(), languageName(e.getKey()), e.getValue()))
                .toList();
        return new PortfolioView(profile.language(), t, csp, assistant, person, nodes, experience, education,
                featured(profile), languages, currentRole(profile), certifications(profile, t));
    }

    static URI askEndpoint(URI base) {
        String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        String host = base.getHost() == null ? "" : base.getHost().toLowerCase(Locale.ROOT);
        boolean local = host.equals("localhost") || host.equals("127.0.0.1");
        if (host.isEmpty() || !(scheme.equals("https") || (scheme.equals("http") && local))) {
            throw new IllegalArgumentException("assistant URL must be https (http only for localhost): " + base);
        }
        if (base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("assistant URL must not contain credentials, query or fragment: " + base);
        }
        String path = base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "");
        return URI.create(scheme + "://" + base.getRawAuthority() + path + "/api/ask");
    }

    /**
     * "Role · Company" of the job held now, so a visitor who lands on the projects first still sees where the
     * person works. Prefers a job that lists technologies; {@code null} when no job is ongoing.
     */
    static String currentRole(Profile profile) {
        return profile.experience().stream()
                .filter(Experience::current)
                .sorted(Comparator.comparingInt((Experience e) -> e.technologies().isEmpty() ? 1 : 0))
                .map(e -> e.role() + " · " + e.company())
                .findFirst().orElse(null);
    }

    /** In the order the author wrote them; issuer and date on one line, like the CV. */
    private static List<PortfolioView.CertificationView> certifications(Profile profile, UiText t) {
        return profile.certifications().stream()
                .map(c -> new PortfolioView.CertificationView(c.title(),
                        java.util.stream.Stream.of(c.issuer() == null ? "" : c.issuer(),
                                        c.issued() == null ? "" : c.issued().format(t.locale()))
                                .filter(s -> !s.isBlank()).collect(Collectors.joining(" · ")),
                        c.link() == null ? "" : c.link().url()))
                .toList();
    }

    /** Name of a language in that same language, as a reader looks for it: "English", "Español". */
    static String languageName(String tag) {
        Locale locale = Locale.forLanguageTag(tag);
        String name = locale.getDisplayLanguage(locale);
        return name.isEmpty() ? tag : name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    /** Language tags like {@code en} or {@code pt-BR}; links relative to the page, {@code ../} allowed, no scheme or host. */
    static Map<String, String> validAlternates(Map<String, String> alternates) {
        Map<String, String> valid = new java.util.LinkedHashMap<>();
        alternates.forEach((lang, href) -> {
            String tag = lang == null ? "" : lang.strip();
            String link = href == null ? "" : href.strip();
            if (!tag.matches("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*")) {
                throw new IllegalArgumentException("alternate language must be a tag like en or pt-BR: " + lang);
            }
            if (!link.matches("(\\.\\./)*([A-Za-z0-9][A-Za-z0-9_-]*(\\.[A-Za-z0-9_-]+)*/?)*")
                    || link.isEmpty() || link.matches(".*[^/]\\.\\..*")) {
                throw new IllegalArgumentException("alternate link must be relative, e.g. en/ or ../: " + href);
            }
            valid.put(tag, link);
        });
        return java.util.Collections.unmodifiableMap(valid);
    }

    /** Only a plain relative path inside the site: no scheme, no host, no {@code ..}, no query. */
    static String relativeHref(String href) {
        String value = href.strip();
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._/-]*") || ("/" + value + "/").contains("/../")) {
            throw new IllegalArgumentException("CV link must be a relative path inside the site, e.g. cv/cv.pdf: " + href);
        }
        return value;
    }

    private static String origin(URI uri) {
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    private static NodeView node(AreaNode node, Profile profile, UiText t) {
        List<String> stats = new ArrayList<>();
        count(stats, node.projects().size(), t.projectNoun());
        count(stats, node.evidence().size(), t.evidenceNoun());
        count(stats, node.labs().size(), t.labNoun());
        count(stats, node.skills().size(), t.skillNoun());

        return new NodeView(
                node.area().name().toLowerCase(Locale.ROOT),
                t.area(node.area()),
                String.join(" · ", stats),
                node.skills().stream().map(Skill::name).toList(),
                node.projects().stream().map(project -> project(project, node, profile, t)).toList(),
                node.evidence().stream().map(e -> evidence(e, t)).toList(),
                node.labs().stream().map(l -> new LabView(l.name(), t.labType(l.type()), l.description())).toList());
    }

    /** Id of a project's card inside one area panel; a project listed in several areas has one card per area. */
    static String projectAnchor(AreaNode node, Project p) {
        return "project-" + node.area().name().toLowerCase(Locale.ROOT) + "-" + p.id();
    }

    private static ProjectView project(Project p, AreaNode node, Profile profile, UiText t) {
        List<EvidenceView> evidence = p.evidenceIds().stream()
                .map(profile::evidence)
                .flatMap(Optional::stream)
                .map(e -> evidence(e, t))
                .toList();
        return new ProjectView(projectAnchor(node, p), p.name(), p.summary(), result(p, profile), p.problem(),
                p.solution(), p.techStack(), p.learnings(), links(p.links()), evidence);
    }

    /**
     * The projects a visitor sees first: the ones the author marked as featured, in profile order. When none is
     * marked, the {@value #FEATURED} best documented ones (result, problem and solution, a link; ties keep profile
     * order). Each card is short and links to the full card in the first area panel that lists the project.
     */
    static List<PortfolioView.FeaturedView> featured(Profile profile) {
        List<Project> projects = profile.projects();
        List<Project> chosen = projects.stream().filter(Project::featured).toList();
        if (chosen.isEmpty()) {
            chosen = IntStream.range(0, projects.size()).boxed()
                    .sorted(Comparator.comparingInt((Integer i) -> -weight(projects.get(i), profile)))
                    .limit(FEATURED)
                    .map(projects::get)
                    .toList();
        }
        List<AreaNode> nodes = ArchitectureMap.from(profile).nodes();
        return chosen.stream()
                .map(p -> new PortfolioView.FeaturedView(p.name(), p.summary(), links(p.links()),
                        nodes.stream().filter(n -> n.projects().contains(p)).findFirst()
                                .map(n -> "#" + projectAnchor(n, p)).orElse(null)))
                .toList();
    }

    private static int weight(Project p, Profile profile) {
        return (result(p, profile).isEmpty() ? 0 : 2) + (isBlank(p.problem()) || isBlank(p.solution()) ? 0 : 2)
                + (p.links().isEmpty() ? 0 : 1);
    }

    private static String result(Project p, Profile profile) {
        return p.evidenceIds().stream().map(profile::evidence).flatMap(Optional::stream)
                .map(Evidence::result).filter(r -> !isBlank(r)).findFirst().orElse("");
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static EvidenceView evidence(Evidence e, UiText t) {
        LinkView link = e.link() == null ? null : new LinkView(e.link().label(), e.link().url());
        return new EvidenceView(e.title(), t.category(e.category()), e.context(), e.action(), e.result(), link);
    }

    private static List<LinkView> links(List<Link> links) {
        return links.stream().map(l -> new LinkView(l.label(), l.url())).toList();
    }

    static String period(Period period, UiText t) {
        return period.format(t.locale(), t.present());
    }

    static String initials(String name) {
        return Arrays.stream(name.trim().split("\\s+"))
                .limit(2)
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT))
                .collect(Collectors.joining());
    }

    private static void count(List<String> stats, int n, UiText.Noun noun) {
        if (n > 0) {
            stats.add(noun.count(n));
        }
    }

    private static InputStream resource(String name) {
        InputStream in = ArchitectureThemeRenderer.class.getResourceAsStream(THEME + name);
        if (in == null) {
            throw new IllegalStateException("Missing theme resource " + THEME + name);
        }
        return in;
    }

    private static String text(String name) {
        try (InputStream in = resource(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
