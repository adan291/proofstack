package dev.proofstack.adapter.out.cv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import dev.proofstack.core.domain.ProfileDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.EvidenceCategory;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.Language;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.Period;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.Skill;
import dev.proofstack.core.port.out.Artifact;

class CvDocxTest {

    @Test
    void isAWordPackageWithEveryRequiredPart() throws Exception {
        Map<String, String> parts = unzip(docx(profile("Ada Lovelace")));

        assertThat(parts).containsKeys("[Content_Types].xml", "_rels/.rels", "word/_rels/document.xml.rels",
                "word/document.xml", "word/styles.xml", "word/numbering.xml");
        for (String xml : parts.values()) {
            parse(xml); // every part must be well-formed XML
        }
    }

    @Test
    void usesNativeHeadingsAndBulletsInTheSameOrderAsTheMarkdown() throws Exception {
        String doc = unzip(docx(profile("Ada Lovelace"))).get("word/document.xml");

        assertThat(doc).contains("<w:pStyle w:val=\"Title\"/></w:pPr><w:r><w:t xml:space=\"preserve\">Ada Lovelace");
        assertThat(doc).contains("<w:numId w:val=\"1\"/>");
        int perfil = doc.indexOf(">Perfil<");
        int skills = doc.indexOf(">Habilidades<");
        int jobs = doc.indexOf(">Experiencia<");
        int projects = doc.indexOf(">Proyectos destacados<");
        int languages = doc.indexOf(">Idiomas<");
        assertThat(perfil).isPositive().isLessThan(skills);
        assertThat(skills).isLessThan(jobs);
        assertThat(jobs).isLessThan(projects);
        assertThat(projects).isLessThan(languages);
    }

    @Test
    void hasNoTablesImagesOrTextBoxes() throws Exception {
        String doc = unzip(docx(profile("Ada Lovelace"))).get("word/document.xml");

        assertThat(doc).doesNotContain("<w:tbl").doesNotContain("<w:drawing").doesNotContain("<w:txbx")
                .doesNotContain("<w:pict");
    }

    @Test
    void escapesUserTextSoTheXmlStaysValid() throws Exception {
        String doc = unzip(docx(profile("Ada <script>&\"x\" \u0001"))).get("word/document.xml");

        parse(doc);
        assertThat(doc).contains("Ada &lt;script&gt;&amp;&quot;x&quot; ").doesNotContain("<script>");
    }

    @Test
    void leavesPlaceholdersOut() throws Exception {
        Profile base = profile("Ada");
        Project draft = new Project("draft", "TODO: name", "Whatever", null, null, List.of(), List.of(), List.of(),
                List.of(Area.BACKEND), List.of());
        Profile profile = new Profile(base.language(), base.person(), base.experience(), base.education(),
                List.of(base.projects().getFirst(), draft), base.skills(), base.evidence(), base.labs(),
                base.languages(), base.certifications());

        assertThat(unzip(docx(profile)).get("word/document.xml")).doesNotContain("TODO");
    }

    @Test
    void theSameProfileAlwaysGivesTheSameBytes() {
        assertThat(docx(profile("Ada"))).isEqualTo(docx(profile("Ada")));
    }

    // ------------------------------------------------------------------ helpers

    private static byte[] docx(Profile profile) {
        return new CvRenderer().render(profile).stream()
                .filter(a -> a.relativePath().equals("cv.docx")).findFirst().map(Artifact::bytes).orElseThrow();
    }

    private static Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                parts.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return parts;
    }

    private static void parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            factory.newDocumentBuilder().parse(in);
        }
    }

    private static Profile profile(String name) {
        PersonalInfo person = new PersonalInfo(name, "Backend Engineer", "Builds systems", "Madrid", null,
                List.of(new Link("GitHub", "https://github.com/example")));
        Evidence proof = new Evidence("ev1", EvidenceCategory.TECHNICAL, "Title", "Context", "Action",
                "Entregado en plazo", null, List.of());
        Experience job = new Experience("job", "Backend Developer", "ACME", "Madrid",
                Period.ongoingSince(ProfileDate.of(2022, 1)), List.of("Built the API"), List.of("Java"), List.of());
        Project project = new Project("p1", "Proofstack", "Portfolio as code", "A real problem", "A real solution",
                List.of("Java"), List.of(), List.of(new Link("GitHub", "https://github.com/example/p")),
                List.of(Area.BACKEND), List.of("ev1"));
        return new Profile("es", person, List.of(job), List.of(), List.of(project),
                List.of(new Skill("Java", Area.BACKEND)), List.of(proof), List.of(),
                List.of(new Language("Español", "Nativo")), List.of());
    }
}
