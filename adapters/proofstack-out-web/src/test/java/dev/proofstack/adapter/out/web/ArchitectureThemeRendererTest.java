package dev.proofstack.adapter.out.web;

import dev.proofstack.core.domain.ProfileDate;
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

        assertThat(html).contains("<a class=\"hero__cv\" href=\"cv/cv.pdf\">Download CV</a>");
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
