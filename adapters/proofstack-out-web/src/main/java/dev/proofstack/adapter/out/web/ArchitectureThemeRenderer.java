package dev.proofstack.adapter.out.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private static final String BASE_CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; base-uri 'none'; form-action 'none'";

    private final Template index;
    private final String styles;
    private final String script;
    private final URI assistantEndpoint;
    private final String cvHref;

    public ArchitectureThemeRenderer() {
        this(null);
    }

    public ArchitectureThemeRenderer(URI assistantBaseUrl) {
        this(assistantBaseUrl, null);
    }

    /**
     * @param assistantBaseUrl base URL of the optional ask service (ADR-007), or {@code null} for none.
     *                         Must be https; plain http is only accepted for localhost development.
     */
    /**
     * @param assistantBaseUrl see {@link #ArchitectureThemeRenderer(URI)}
     * @param cvHref           relative path of the CV published with the site (e.g. {@code cv/cv.pdf}), or {@code null}
     */
    public ArchitectureThemeRenderer(URI assistantBaseUrl, String cvHref) {
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
                p.email(), links(p.links()), cv);

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
        return new PortfolioView(profile.language(), t, csp, assistant, person, nodes, experience, education);
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
                node.projects().stream().map(project -> project(project, profile, t)).toList(),
                node.evidence().stream().map(e -> evidence(e, t)).toList(),
                node.labs().stream().map(l -> new LabView(l.name(), t.labType(l.type()), l.description())).toList());
    }

    private static ProjectView project(Project p, Profile profile, UiText t) {
        List<EvidenceView> evidence = p.evidenceIds().stream()
                .map(profile::evidence)
                .flatMap(Optional::stream)
                .map(e -> evidence(e, t))
                .toList();
        return new ProjectView(p.name(), p.summary(), p.problem(), p.solution(), p.techStack(), p.learnings(),
                links(p.links()), evidence);
    }

    private static EvidenceView evidence(Evidence e, UiText t) {
        LinkView link = e.link() == null ? null : new LinkView(e.link().label(), e.link().url());
        return new EvidenceView(e.title(), t.category(e.category()), e.context(), e.action(), e.result(), link);
    }

    private static List<LinkView> links(List<Link> links) {
        return links.stream().map(l -> new LinkView(l.label(), l.url())).toList();
    }

    static String period(Period period, UiText t) {
        DateTimeFormatter month = DateTimeFormatter.ofPattern("MMM yyyy", t.locale());
        String start = month.format(period.start());
        String end = period.endDate().map(month::format).orElse(t.present());
        return start + " – " + end;
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
