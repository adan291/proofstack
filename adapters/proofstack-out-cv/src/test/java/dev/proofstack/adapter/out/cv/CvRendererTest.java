package dev.proofstack.adapter.out.cv;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.Certification;
import dev.proofstack.core.domain.Education;
import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.EvidenceCategory;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.Language;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.Period;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileDate;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.Skill;
import dev.proofstack.core.port.out.Artifact;

class CvRendererTest {

    @Test
    void producesMarkdownHtmlAndStylesheet() {
        assertThat(new CvRenderer().render(profile("es", "Ada Lovelace")))
                .extracting(Artifact::relativePath)
                .containsExactly("cv.md", "cv.html", "cv.css", "cv.docx");
    }

    @Test
    void markdownHasTheSectionsInOrderWithSpanishLabels() {
        String md = markdown(new CvRenderer(), profile("es", "Ada Lovelace"));

        assertThat(md).startsWith("# Ada Lovelace\n\nBackend Engineer\n");
        assertThat(md.indexOf("## Perfil")).isLessThan(md.indexOf("## Habilidades"));
        assertThat(md.indexOf("## Habilidades")).isLessThan(md.indexOf("## Experiencia"));
        assertThat(md.indexOf("## Experiencia")).isLessThan(md.indexOf("## Proyectos destacados"));
        assertThat(md.indexOf("## Proyectos destacados")).isLessThan(md.indexOf("## Formación"));
        assertThat(md).contains("- Resultado: Entregado en plazo").contains("*Madrid · ene 2022 – Actualidad*");
    }

    /** A year alone is printed alone, in every format; never "Jan 2018" (year-dates R2.1). */
    @Test
    void aYearWithoutMonthIsPrintedAsTheYear() {
        Education study = new Education("dam", "CFGS DAM", "IES Ejemplo",
                new Period(ProfileDate.of(2018), ProfileDate.of(2020)), List.of());
        Profile profile = new Profile("en", new PersonalInfo("Ada", "Dev", null, null, null, List.of()),
                List.of(), List.of(study), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(new Certification("AWS Developer", "Amazon", ProfileDate.of(2023), null)));

        String md = markdown(new CvRenderer(), profile);

        assertThat(md).contains("*2018 – 2020*").contains("Amazon · 2023").doesNotContain("Jan");
        assertThat(html(profile)).contains("2018 – 2020").doesNotContain("Jan");
        assertThat(docxText(new CvRenderer().render(profile))).contains("2018 – 2020").doesNotContain("Jan");
    }

    /** A period the importer could not read is a placeholder: printing it would put an invented month on the CV. */
    @Test
    void aPeriodFlaggedAsUnknownIsLeftOut() {
        String flag = dev.proofstack.core.domain.TodoCounter.DATES + ": LinkedIn only gives the year";
        Experience job = new Experience("old", "Developer", "Old Co", null,
                new Period(ProfileDate.of(2019, 1), ProfileDate.of(2019, 1)), List.of("Did things", flag), List.of(),
                List.of());
        Education study = new Education("dam", "CFGS DAM", "IES Ejemplo",
                new Period(ProfileDate.of(2018, 1), ProfileDate.of(2020, 1)), List.of(flag));
        Profile profile = new Profile("en", new PersonalInfo("Ada", "Dev", null, null, null, List.of()),
                List.of(job), List.of(study), List.of(), List.of(), List.of(), List.of());

        List<Artifact> files = new CvRenderer().render(profile);
        String md = markdown(new CvRenderer(), profile);
        String html = html(profile);
        String docx = docxText(files);

        for (String out : List.of(md, html, docx)) {
            assertThat(out).contains("Old Co").contains("IES Ejemplo").doesNotContain("Jan 2019").doesNotContain("2018")
                    .doesNotContain("TODO");
        }
        assertThat(md).doesNotContain("**").doesNotContain("*\n");
        assertThat(html).doesNotContain("<p class=\"meta\"></p>");
    }

    private static String docxText(List<Artifact> files) {
        byte[] docx = files.stream().filter(a -> a.relativePath().equals("cv.docx")).findFirst().orElseThrow().bytes();
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(docx))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.getName().equals("word/document.xml")) {
                    return new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        throw new AssertionError("no word/document.xml");
    }

    @Test
    void unknownLanguageFallsBackToEnglish() {
        String md = markdown(new CvRenderer(), profile("fr", "Ada Lovelace"));

        assertThat(md).contains("## Skills").contains("## Selected projects").contains("Present");
    }

    @Test
    void htmlEscapesUserContentAndHasNoScriptNorInlineStyle() {
        String html = html(profile("en", "<script>alert('x')</script>"));

        assertThat(html).doesNotContain("<script>alert").contains("&lt;script&gt;");
        assertThat(html).doesNotContain("<script").doesNotContain("style=\"");
        assertThat(html).contains("default-src 'none'").contains("href=\"cv.css\"");
    }

    @Test
    void htmlRepeatsEachSectionOnce() {
        String html = html(profile("en", "Ada Lovelace"));

        for (String title : List.of("Profile", "Skills", "Experience", "Selected projects", "Education")) {
            assertThat(html.split("<h2>" + title + "</h2>", -1)).hasSize(2);
        }
    }

    @Test
    void placeholdersAreLeftOutNotPrinted() {
        Profile profile = profileWith(
                project("p1", "Real project", "Does a thing", List.of(Area.BACKEND), "ev1"),
                project("p2", "TODO: name", "Whatever", List.of(Area.BACKEND)),
                project("p3", "Half done", "TODO: describe", List.of(Area.BACKEND)));

        String md = markdown(new CvRenderer(), profile);

        assertThat(md).contains("Real project").doesNotContain("TODO").doesNotContain("Half done");
    }

    @Test
    void placeholderResultIsNotShown() {
        Evidence draft = new Evidence("ev1", EvidenceCategory.TECHNICAL, "Title", "Ctx", "Act",
                "TODO: result", null, List.of());
        Profile profile = new Profile("en", person("Ada"), List.of(), List.of(),
                List.of(project("p1", "Real project", "Does a thing", List.of(Area.BACKEND), "ev1")),
                List.of(), List.of(draft), List.of());

        assertThat(markdown(new CvRenderer(), profile)).doesNotContain("TODO").doesNotContain("Result:");
    }

    @Test
    void focusPutsMatchingProjectsAndSkillsFirst() {
        Profile profile = new Profile("en", person("Ada"), List.of(), List.of(),
                List.of(project("b", "Backend thing", "B", List.of(Area.BACKEND)),
                        project("a", "AI thing", "A", List.of(Area.AI))),
                List.of(new Skill("Java", Area.BACKEND), new Skill("Agents", Area.AI)), List.of(), List.of());

        String neutral = markdown(new CvRenderer(), profile);
        String focused = markdown(new CvRenderer(new CvOptions(Area.AI, 5)), profile);

        assertThat(neutral.indexOf("Backend thing")).isLessThan(neutral.indexOf("AI thing"));
        assertThat(neutral.indexOf("**Backend:**")).isLessThan(neutral.indexOf("**AI:**"));
        assertThat(focused.indexOf("AI thing")).isLessThan(focused.indexOf("Backend thing"));
        assertThat(focused.indexOf("**AI:**")).isLessThan(focused.indexOf("**Backend:**"));
    }

    @Test
    void projectsWithProblemSolutionAndEvidenceBeatThinOnes() {
        Profile profile = profileWith(
                project("thin", "Thin", "Just a summary", List.of(Area.BACKEND)),
                project("rich", "Rich", "Has proof", List.of(Area.BACKEND), "ev1"));

        String md = markdown(new CvRenderer(), profile);

        assertThat(md.indexOf("Rich")).isLessThan(md.indexOf("Thin"));
    }

    @Test
    void limitsTheNumberOfProjects() {
        Profile profile = profileWith(
                project("one", "First", "1", List.of(Area.BACKEND)),
                project("two", "Second", "2", List.of(Area.BACKEND)),
                project("three", "Third", "3", List.of(Area.BACKEND)));

        String md = markdown(new CvRenderer(new CvOptions(null, 2)), profile);

        assertThat(md).contains("First").contains("Second").doesNotContain("Third");
    }

    @Test
    void newestExperienceComesFirst() {
        Experience old = new Experience("old", "Intern", "Old Co", null,
                new Period(ProfileDate.of(2014, 1), ProfileDate.of(2014, 6)), List.of(), List.of(), List.of());
        Experience recent = new Experience("new", "Engineer", "New Co", null,
                Period.ongoingSince(ProfileDate.of(2022, 1)), List.of(), List.of(), List.of());
        Profile profile = new Profile("en", person("Ada"), List.of(old, recent), List.of(), List.of(), List.of(),
                List.of(), List.of());

        String md = markdown(new CvRenderer(), profile);

        assertThat(md.indexOf("New Co")).isLessThan(md.indexOf("Old Co"));
    }

    @Test
    void emptySectionsAreOmitted() {
        Profile profile = new Profile("en", person("Ada"), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());

        String md = markdown(new CvRenderer(), profile);

        assertThat(md).doesNotContain("## Skills").doesNotContain("## Experience").doesNotContain("## Education");
        assertThat(html(profile)).doesNotContain("<h2>Skills</h2>").doesNotContain("<h2>Experience</h2>");
    }

    @Test
    void showsLanguagesAndCertificationsLastWithTheirLinks() {
        Profile base = profile("es", "Ada");
        Profile profile = new Profile(base.language(), base.person(), base.experience(), base.education(),
                base.projects(), base.skills(), base.evidence(), base.labs(),
                List.of(new Language("Español", "Nativo"), new Language("Inglés", null)),
                List.of(new Certification("Java SE 21", "Oracle", ProfileDate.of(2024, 3),
                        new Link("Credential", "https://example.com/c/1")),
                        new Certification("Scrum Master", null, null, null)));

        String md = markdown(new CvRenderer(), profile);
        String html = html(profile);

        assertThat(md).contains("## Certificaciones", "- [Java SE 21](https://example.com/c/1) — Oracle · mar 2024",
                "- Scrum Master", "## Idiomas", "- Español (Nativo)", "- Inglés\n");
        assertThat(md.indexOf("## Formación")).isLessThan(md.indexOf("## Certificaciones"));
        assertThat(md.indexOf("## Certificaciones")).isLessThan(md.indexOf("## Idiomas"));
        assertThat(html).contains("<h2>Certificaciones</h2>", "<h2>Idiomas</h2>", "href=\"https://example.com/c/1\"");
    }

    @Test
    void omitsLanguagesAndCertificationsWhenThereAreNone() {
        String md = markdown(new CvRenderer(), profile("es", "Ada"));

        assertThat(md).doesNotContain("## Idiomas").doesNotContain("## Certificaciones");
    }

    @Test
    void aJobPostingReordersProjectsAndSkillsButAddsNothing() {
        Profile profile = new Profile("en", person("Ada"), List.of(), List.of(),
                List.of(project("a", "Search engine", "Elasticsearch based search", List.of(Area.BACKEND)),
                        project("b", "Billing events", "Outbox pattern with Kafka", List.of(Area.BACKEND))),
                List.of(new Skill("Java", Area.BACKEND), new Skill("Kafka", Area.MESSAGING),
                        new Skill("Swing", Area.BACKEND)), List.of(), List.of());
        JobOffer offer = JobOffer.parse("We need someone with Kafka and the outbox pattern. Kafka is key.");

        String neutral = markdown(new CvRenderer(), profile);
        String tailored = markdown(new CvRenderer(new CvOptions(null, 5, offer)), profile);

        assertThat(neutral.indexOf("Search engine")).isLessThan(neutral.indexOf("Billing events"));
        assertThat(tailored.indexOf("Billing events")).isLessThan(tailored.indexOf("Search engine"));
        assertThat(tailored.indexOf("**Messaging:**")).isLessThan(tailored.indexOf("**Backend:**"));
        assertThat(tailored.lines().filter(l -> !l.isBlank()).count())
                .isEqualTo(neutral.lines().filter(l -> !l.isBlank()).count());
    }

    /**
     * Review H11: words the profile says in its summary, education, evidence or languages are not gaps. The vocabulary
     * only looked at skills, jobs, projects and certifications, so "inglés" was a gap next to {@code Inglés: B2}.
     */
    @Test
    void jobFitDoesNotReportAsGapsWordsTheProfileSaysElsewhere() {
        Profile profile = new Profile("es",
                new PersonalInfo("Ada", "Backend Developer", "Trabajo con Kubernetes a diario.", null, null, List.of()),
                List.of(),
                List.of(new Education("dam", "Desarrollo de aplicaciones multiplataforma", "IES Ejemplo",
                        new Period(ProfileDate.of(2018), ProfileDate.of(2020)), List.of())),
                List.of(), List.of(),
                List.of(new Evidence("ev", EvidenceCategory.TECHNICAL, "Migración a Terraform", "Un contexto",
                        "Una acción", "Un resultado", null, List.of())),
                List.of(), List.of(new Language("Inglés", "B2")), List.of());
        JobOffer offer = JobOffer.parse("Kubernetes, Kubernetes. Terraform, Terraform. Multiplataforma, multiplataforma. "
                + "Inglés, inglés. Rust, Rust.");

        JobFit fit = JobFit.of(profile, offer);

        assertThat(fit.gaps()).containsExactly("rust");
        assertThat(fit.matched()).contains("kubernetes", "terraform", "multiplataforma", "ingles");
    }

    @Test
    void jobFitListsWhatMatchesAndWhatTheProfileNeverSays() {
        Profile profile = new Profile("en", person("Ada"), List.of(), List.of(),
                List.of(project("b", "Billing events", "Outbox pattern with Kafka", List.of(Area.BACKEND))),
                List.of(new Skill("Java", Area.BACKEND)), List.of(), List.of());
        JobOffer offer = JobOffer.parse("Kafka, Kubernetes, Kubernetes, Terraform, Java 21, Node.js. Experience required.");

        JobFit fit = JobFit.of(profile, offer);

        assertThat(fit.matched()).contains("kafka", "java");
        assertThat(fit.gaps()).contains("kubernetes", "node.js").doesNotContain("kafka", "experience");
    }

    @Test
    void rejectsAnOutOfRangeProjectCount() {
        assertThatThrownBy(() -> new CvOptions(null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CvOptions(null, 99)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static String markdown(CvRenderer renderer, Profile profile) {
        return content(renderer.render(profile), "cv.md");
    }

    private static String html(Profile profile) {
        return content(new CvRenderer().render(profile), "cv.html");
    }

    private static String content(List<Artifact> artifacts, String path) {
        return artifacts.stream().filter(a -> a.relativePath().equals(path)).findFirst().orElseThrow().content();
    }

    private static PersonalInfo person(String name) {
        return new PersonalInfo(name, "Backend Engineer", "Builds systems", "Madrid", null,
                List.of(new Link("GitHub", "https://github.com/example")));
    }

    private static Project project(String id, String name, String summary, List<Area> areas, String... evidence) {
        boolean rich = evidence.length > 0;
        return new Project(id, name, summary, rich ? "A real problem" : null, rich ? "A real solution" : null,
                List.of("Java"), List.of(), List.of(), areas, List.of(evidence));
    }

    private static Profile profileWith(Project... projects) {
        Evidence proof = new Evidence("ev1", EvidenceCategory.TECHNICAL, "Title", "Context", "Action",
                "Entregado en plazo", null, List.of());
        return new Profile("en", person("Ada"), List.of(), List.of(), List.of(projects), List.of(),
                List.of(proof), List.of());
    }

    private static Profile profile(String language, String name) {
        Evidence proof = new Evidence("ev1", EvidenceCategory.TECHNICAL, "Title", "Context", "Action",
                "Entregado en plazo", new Link("Repo", "https://github.com/example/repo"), List.of());
        Experience job = new Experience("job", "Backend Developer", "ACME", "Madrid",
                Period.ongoingSince(ProfileDate.of(2022, 1)), List.of("Built the API"), List.of("Java"), List.of());
        Education study = new Education("edu", "DAM", "Instituto", new Period(ProfileDate.of(2019, 9),
                ProfileDate.of(2020, 6)), List.of());
        return new Profile(language, person(name), List.of(job), List.of(study),
                List.of(project("p1", "Proofstack", "Portfolio as code", List.of(Area.BACKEND), "ev1")),
                List.of(new Skill("Java", Area.BACKEND)), List.of(proof), List.of());
    }
}
