package dev.proofstack.adapter.out.web;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.EvidenceCategory;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.Period;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileDate;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.Skill;
import dev.proofstack.core.port.out.Artifact;

class ArchitectureThemeRendererTest {

    private final ArchitectureThemeRenderer renderer = new ArchitectureThemeRenderer();

    @Test
    void cvLinkAppearsOnlyWhenRequested() {
        assertThat(html(profile("Ada Lovelace"))).doesNotContain("hero__cv");

        String html = new ArchitectureThemeRenderer(null, "cv/cv.pdf").render(profile("Ada Lovelace")).stream()
                .filter(a -> a.relativePath().equals("index.html")).findFirst().orElseThrow().content();

        assertThat(html).contains("<a class=\"hero__cv\" href=\"cv/cv.pdf\" target=\"_blank\" rel=\"noopener\">Download CV</a>");
    }

    @Test
    void cvLinkMustBeARelativePathInsideTheSite() {
        for (String bad : new String[]{"https://evil.example/cv.pdf", "javascript:alert(1)", "../cv.pdf", "/cv.pdf",
                "cv/../../x", "cv.pdf?x=1", "cv\"onclick=\"x"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ArchitectureThemeRenderer(null, bad))
                    .as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void featuresTheBestDocumentedProjectsFirstAndKeepsProfileOrderOnTies() {
        Project bare = new Project("a", "Bare", "Only a summary", null, null, List.of(), List.of(), List.of(),
                List.of(Area.BACKEND), List.of());
        Project documented = new Project("b", "Documented", "With problem and solution", "Why", "How",
                List.of("Java"), List.of(), List.of(), List.of(Area.BACKEND), List.of());
        Project linked = new Project("c", "Linked", "With a link", null, null, List.of(), List.of(),
                List.of(new Link("GitHub", "https://github.com/example/c")), List.of(Area.BACKEND), List.of());
        Project another = new Project("d", "Another", "Also bare", null, null, List.of(), List.of(), List.of(),
                List.of(Area.BACKEND), List.of());
        Profile base = profile("Ada");
        Profile profile = new Profile(base.person(), base.experience(), base.education(),
                List.of(bare, documented, linked, another), base.skills(), base.evidence(), base.labs());
        assertThat(ArchitectureThemeRenderer.featured(profile))
                .extracting(PortfolioView.FeaturedView::name).containsExactly("Documented", "Linked", "Bare");
        String html = html(profile);
        assertThat(html).contains("id=\"featured-title\"");
        assertThat(html.indexOf("featured-title")).isLessThan(html.indexOf("system-title"));
    }

    @Test
    void producesStaticSiteFiles() {
        assertThat(renderer.render(profile("Ada Lovelace")))
                .extracting(Artifact::relativePath)
                .containsExactly("index.html", "styles.css", "app.js");
    }

    @Test
    void escapesUserContent() {
        String html = html(profile("<script>alert('x')</script>"));

        assertThat(html).doesNotContain("<script>alert");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void rendersAccessibleNodesLinkedToPanels() {
        String html = html(profile("Ada Lovelace"));

        assertThat(html)
                .contains("id=\"node-backend\" aria-controls=\"panel-backend\" aria-expanded=\"false\"")
                .contains("id=\"panel-backend\"")
                .contains("id=\"node-leadership\"")
                .contains("lang=\"en\"")
                .contains("Skip to content");
    }

    @Test
    void rendersProjectsEvidenceAndExperience() {
        String html = html(profile("Ada Lovelace"));

        assertThat(html)
                .contains("Tomcat 9 → 10 migration")
                .contains("<strong>Problem.</strong> javax APIs")
                .contains("Coordinated a team of 20")
                .contains("Jan 2022 – Present")
                .contains("href=\"https://github.com/example\"");
    }

    @Test
    void includesFooterAndCsp() {
        String html = html(profile("Ada Lovelace"));

        assertThat(html).contains("Built with Proofstack").contains("Content-Security-Policy");
    }

    @Test
    void helpersFormatNamesAndPeriods() {
        assertThat(ArchitectureThemeRenderer.initials("Ada  Lovelace Byron")).isEqualTo("AL");
        assertThat(ArchitectureThemeRenderer.period(new Period(ProfileDate.of(2020, 3), ProfileDate.of(2021, 12)), UiText.ENGLISH))
                .isEqualTo("Mar 2020 – Dec 2021");
        assertThat(ArchitectureThemeRenderer.period(new Period(ProfileDate.of(2018), ProfileDate.of(2020)), UiText.ENGLISH))
                .isEqualTo("2018 – 2020");
    }

    @Test
    void spanishProfileGetsSpanishUiAndLangAttribute() {
        Profile english = profile("Ada Lovelace");
        Profile spanish = new Profile("es", english.person(), english.experience(), english.education(),
                english.projects(), english.skills(), english.evidence(), english.labs());

        String html = html(spanish);

        assertThat(html)
                .contains("<html lang=\"es\">")
                .contains("Saltar al contenido")
                .contains("Arquitectura del sistema")
                .contains("<strong>Problema.</strong>")
                .contains("Liderazgo")
                .contains("Actualidad")
                .contains("Hecho con Proofstack")
                .doesNotContain("Skip to content");
    }

    @Test
    void withoutAssistantThereIsNoWidgetAndNoConnectSrc() {
        String html = html(profile("Ada Lovelace"));

        assertThat(html).doesNotContain("data-ask").doesNotContain("connect-src");
    }

    @Test
    void assistantAddsHiddenWidgetAndScopedConnectSrc() {
        String html = new ArchitectureThemeRenderer(java.net.URI.create("https://ai.example.dev/"))
                .render(profile("Ada Lovelace")).getFirst().content();

        assertThat(html)
                .contains("connect-src https://ai.example.dev\"")
                .contains("data-endpoint=\"https://ai.example.dev/api/ask\"")
                .contains("hidden data-ask")
                .contains("aria-live=\"polite\"")
                .contains("Ask my portfolio");
    }

    @Test
    void assistantUrlMustBeHttpsExceptLocalhost() {
        assertThat(ArchitectureThemeRenderer.askEndpoint(java.net.URI.create("http://localhost:8080")))
                .hasToString("http://localhost:8080/api/ask");
        assertThat(ArchitectureThemeRenderer.askEndpoint(java.net.URI.create("https://x.dev/ai/")))
                .hasToString("https://x.dev/ai/api/ask");
        for (String bad : new String[]{"http://example.com", "javascript:alert(1)", "https://u:p@x.dev",
                "https://x.dev/?q=1", "ftp://x.dev"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> new ArchitectureThemeRenderer(java.net.URI.create(bad)))
                    .as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void unknownLanguageFallsBackToEnglishUi() {
        assertThat(UiText.forLanguage("de")).isSameAs(UiText.ENGLISH);
        assertThat(UiText.forLanguage("es-MX")).isSameAs(UiText.SPANISH);
    }

    /** The featured card is short and points to the full card; the result is shown once, in the area panel. */
    @Test
    void featuredCardLinksToTheFullCardInsteadOfRepeatingIt() {
        String html = html(profile("Ada"));
        String featured = html.substring(html.indexOf("id=\"featured\""), html.indexOf("id=\"system\""));

        assertThat(featured).contains("<a class=\"card__detail\" href=\"#project-backend-tomcat\" data-goto>See details</a>")
                .doesNotContain("card__result").doesNotContain("class=\"chip\"");
        assertThat(html).contains("<article class=\"card\" id=\"project-backend-tomcat\" tabindex=\"-1\">");
    }

    @Test
    void certificationsAreListedUnderEducationWithTheirLink() {
        Profile base = profile("Ada");
        Profile profile = new Profile("en", base.person(), base.experience(), List.of(), base.projects(),
                base.skills(), base.evidence(), base.labs(), List.of(),
                List.of(new dev.proofstack.core.domain.Certification("Java fundamentals", "FOC", ProfileDate.of(2022, 3),
                                new Link("PDF", "https://example.org/java.pdf")),
                        new dev.proofstack.core.domain.Certification("No link", null, null, null)));

        String html = html(profile);

        assertThat(html).contains("<a href=\"#education\">Education</a>").contains("<h3 class=\"timeline__other-title\">Certifications</h3>")
                .contains("<span class=\"cert__title\">Java fundamentals</span>")
                .contains("<span class=\"cert__detail\">FOC · Mar 2022</span>")
                .contains("<a class=\"cert__link\" href=\"https://example.org/java.pdf\" target=\"_blank\" rel=\"noopener noreferrer\">View certificate</a>");
        assertThat(html.substring(html.indexOf("No link"))).startsWith("No link</span>\n        \n        \n      </li>");
        assertThat(html(base)).doesNotContain("class=\"certs\"");
    }

    @Test
    void contactShowsTheEmailAddressItself() {
        Profile base = profile("Ada");
        PersonalInfo p = base.person();
        Profile profile = new Profile(new PersonalInfo(p.name(), p.headline(), p.summary(), p.location(),
                "ada@example.org", p.links()), base.experience(), base.education(), base.projects(), base.skills(),
                base.evidence(), base.labs());

        String contact = html(profile).substring(html(profile).indexOf("id=\"contact\""));

        assertThat(contact).contains("<a href=\"mailto:ada@example.org\">ada@example.org</a>")
                .contains("data-copy=\"ada@example.org\"").contains("<p class=\"contact__meta\">Spain</p>")
                .doesNotContain("tel:");
    }

    @Test
    void contactUsesTheOwnMessageAndThePublicPhone() {
        Profile base = profile("Ada");
        PersonalInfo p = base.person();
        Profile profile = new Profile(new PersonalInfo(p.name(), p.headline(), p.summary(), p.location(), null,
                p.links(), "+34 600 11 22 33", "Available from June.\nRemote or hybrid."), base.experience(),
                base.education(), base.projects(), base.skills(), base.evidence(), base.labs());

        String contact = html(profile).substring(html(profile).indexOf("id=\"contact\""));

        assertThat(contact).contains("<p class=\"contact__text contact__text--own\">Available from June.\nRemote or hybrid.</p>")
                .doesNotContain("Let's talk").contains("<a href=\"tel:+34600112233\">+34 600 11 22 33</a>");
    }

    @Test
    void markedProjectsAreTheFeaturedOnesInProfileOrder() {
        Project documented = new Project("b", "Documented", "With problem and solution", "Why", "How",
                List.of("Java"), List.of(), List.of(), List.of(Area.BACKEND), List.of());
        Project second = new Project("c", "Second pick", "Marked", null, null, List.of(), List.of(), List.of(),
                List.of(Area.BACKEND), List.of(), true);
        Project first = new Project("a", "First pick", "Marked", null, null, List.of(), List.of(), List.of(),
                List.of(Area.BACKEND), List.of(), true);
        Profile base = profile("Ada");
        Profile profile = new Profile(base.person(), base.experience(), base.education(),
                List.of(documented, first, second), base.skills(), base.evidence(), base.labs());

        assertThat(ArchitectureThemeRenderer.featured(profile))
                .extracting(PortfolioView.FeaturedView::name).containsExactly("First pick", "Second pick");
    }

    @Test
    void alternateLanguagesAddASwitchAndHreflangLinks() {
        String html = new ArchitectureThemeRenderer(null, null, java.util.Map.of("es", "../"))
                .render(profile("Ada")).getFirst().content();

        assertThat(html).contains("<a href=\"../\" hreflang=\"es\" lang=\"es\">Español</a>");
        assertThat(html(profile("Ada"))).doesNotContain("hreflang");
    }

    @Test
    void alternateLinksMustBeRelative() {
        for (String bad : new String[]{"https://evil.example/", "javascript:alert(1)", "/en/", "en/../../x", "en/..", "en\"x", "//evil.example/", "en?x=1"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> new ArchitectureThemeRenderer(null, null, java.util.Map.of("en", bad)))
                    .as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ArchitectureThemeRenderer(null, null, java.util.Map.of("<x>", "en/")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jobsWithoutTechnologiesGoToACompactOtherExperienceList() {
        Profile base = profile("Ada");
        Experience other = new Experience("shop", "Shop assistant", "Store", null,
                Period.ongoingSince(ProfileDate.of(2018, 1)), List.of("Customer service"), List.of(), List.of());
        Profile profile = new Profile(base.person(), List.of(base.experience().getFirst(), other), base.education(),
                base.projects(), base.skills(), base.evidence(), base.labs());

        String html = html(profile);

        assertThat(html).contains("class=\"other-jobs\"").contains("<strong>Shop assistant</strong> · Store");
        assertThat(html.indexOf("Backend Developer")).isLessThan(html.indexOf("Other experience"));
        assertThat(html(base)).doesNotContain("other-jobs");
    }

    @Test
    void heroShowsTheCurrentJobAndPrefersATechnicalOne() {
        Profile base = profile("Ada");
        Experience sideJob = new Experience("bar", "Waiter", "Bar", null,
                Period.ongoingSince(ProfileDate.of(2023, 1)), List.of(), List.of(), List.of());
        Profile profile = new Profile(base.person(), List.of(sideJob, base.experience().getFirst()), base.education(),
                base.projects(), base.skills(), base.evidence(), base.labs());

        assertThat(html(profile)).contains("<p class=\"hero__current\"><span>Currently:</span> Backend Developer · ACME</p>");
        Experience past = new Experience("old", "Dev", "Old", null,
                new Period(ProfileDate.of(2019, 1), ProfileDate.of(2020, 1)), List.of(), List.of("Java"), List.of());
        Profile noCurrent = new Profile(base.person(), List.of(past), base.education(), base.projects(),
                base.skills(), base.evidence(), base.labs());
        assertThat(html(noCurrent)).doesNotContain("hero__current");
    }

    @Test
    void contactTextOnlyMentionsTheCvWhenThereIsOne() {
        assertThat(html(profile("Ada"))).contains("Write to me or find me online.").doesNotContain("download my CV");
        String withCv = new ArchitectureThemeRenderer(null, "cv/cv.pdf").render(profile("Ada")).getFirst().content();
        assertThat(withCv).contains("Write to me or download my CV.");
    }

    private String html(Profile profile) {
        return renderer.render(profile).getFirst().content();
    }

    private static Profile profile(String name) {
        PersonalInfo person = new PersonalInfo(name, "Backend Engineer", "Builds systems", "Spain", null,
                List.of(new Link("GitHub", "https://github.com/example")));
        Project migration = new Project("tomcat", "Tomcat 9 → 10 migration", "Moved to Jakarta EE",
                "javax APIs incompatible with Tomcat 10", "Progressive migration to jakarta.*",
                List.of("Java", "Tomcat"), List.of("Plan dependency upgrades first"), List.of(),
                List.of(Area.BACKEND), List.of("team"));
        Evidence team = new Evidence("team", EvidenceCategory.LEADERSHIP, "Coordinated a team of 20",
                "Large delivery", "Planning and follow-up", "Delivered", null, List.of());
        Experience job = new Experience("job", "Backend Developer", "ACME", null,
                Period.ongoingSince(ProfileDate.of(2022, 1)), List.of(), List.of("Java"), List.of());
        return new Profile(person, List.of(job), List.of(), List.of(migration),
                List.of(new Skill("Java", Area.BACKEND)), List.of(team), List.of());
    }
}
