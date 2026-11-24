package dev.proofstack.adapter.in.linkedin;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinkedInExportImporterTest {

    @TempDir
    Path tmp;

    private final LinkedInExportImporter importer = new LinkedInExportImporter();

    private Path zip(Map<String, String> files) throws IOException {
        Path zip = tmp.resolve("Basic_LinkedInDataExport.zip");
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(out)) {
            for (var e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return zip;
    }

    private Map<String, String> export() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("Profile.csv", "\uFEFFFirst Name,Last Name,Maiden Name,Address,Birth Date,Headline,Summary,Industry,Zip Code,Geo Location,Twitter Handles,Websites,Instant Messengers\n"
                + "Adán,Sánchez,,Calle Falsa 1,1 Jan 1990,\"Backend Developer | Java, Spring\",\"Línea 1\nLínea 2\",IT,28000,\"Madrid, Spain\",,\"[PORTFOLIO:https://example.dev],[OTHER:https://github.com/example]\",\n");
        files.put("Positions.csv", "Company Name,Title,Description,Location,Started On,Finished On\n"
                + "ACME,Backend Developer,\"- Migré Tomcat 9 a 10\n- Coordiné a 20 personas\",Madrid,ene 2022,\n"
                + "ACME,Junior Developer,,Madrid,Mar 2019,Dec 2021\n");
        files.put("Education.csv", "School Name,Start Date,End Date,Notes,Degree Name,Activities\n"
                + "IES Ejemplo,,2020,,CFGS DAM,\n");
        files.put("Skills.csv", "Name\nJava\nApache Kafka\nDocker\njava\nLiderazgo de equipos\nMaintenance\n");
        files.put("Projects.csv", "Title,Description,Url,Started On,Finished On\n"
                + "Búsqueda semántica,\"Embeddings, \"\"vector\"\" search\",https://github.com/example/search,Jan 2024,\n");
        files.put("Email Addresses.csv", "Email Address,Confirmed,Primary,Updated On\nsecret@example.com,Yes,Yes,\n");
        return files;
    }

    @Test
    void importsAllSectionsFromExport() throws IOException {
        Profile p = importer.importProfile(zip(export()));

        assertThat(p.person().name()).isEqualTo("Adán Sánchez");
        assertThat(p.person().headline()).isEqualTo("Backend Developer | Java, Spring");
        assertThat(p.person().summary()).isEqualTo("Línea 1\nLínea 2");
        assertThat(p.person().location()).isEqualTo("Madrid, Spain");
        assertThat(p.person().links()).extracting("url")
                .containsExactly("https://example.dev", "https://github.com/example");

        Experience current = p.experience().getFirst();
        assertThat(current.id()).isEqualTo("acme");
        assertThat(current.current()).isTrue();
        assertThat(current.period().start()).isEqualTo(YearMonth.of(2022, 1));
        assertThat(current.highlights()).containsExactly("Migré Tomcat 9 a 10", "Coordiné a 20 personas");
        assertThat(p.experience().get(1).id()).isEqualTo("acme-2");
        assertThat(p.experience().get(1).highlights().getFirst()).startsWith("TODO");

        assertThat(p.projects().getFirst().id()).isEqualTo("busqueda-semantica");
        assertThat(p.projects().getFirst().summary()).isEqualTo("Embeddings, \"vector\" search");
    }

    @Test
    void importsLanguagesAndCertifications() throws IOException {
        Map<String, String> files = export();
        files.put("Languages.csv", "Name,Proficiency\nEspañol,Native or bilingual proficiency\nEnglish,\n,Professional\n");
        files.put("Certifications.csv", "Name,Url,Authority,Started On,Finished On,License Number\n"
                + "Java SE 21,https://example.com/c/1,Oracle,Mar 2024,,123\n"
                + "Sin enlace,,,,,\n");

        Profile p = importer.importProfile(zip(files));

        assertThat(p.languages()).extracting("name", "level").containsExactly(
                org.assertj.core.groups.Tuple.tuple("Español", "Native or bilingual proficiency"),
                org.assertj.core.groups.Tuple.tuple("English", null));
        assertThat(p.certifications()).extracting("title").containsExactly("Java SE 21", "Sin enlace");
        assertThat(p.certifications().getFirst().issuer()).isEqualTo("Oracle");
        assertThat(p.certifications().getFirst().issued()).isEqualTo(YearMonth.of(2024, 3));
        assertThat(p.certifications().getFirst().link().url()).isEqualTo("https://example.com/c/1");
        assertThat(p.certifications().get(1).link()).isNull();
    }

    @Test
    void neverImportsEmailOrPhone() throws IOException {
        Profile p = importer.importProfile(zip(export()));

        assertThat(p.person().email()).isNull();
        assertThat(p.toString()).doesNotContain("secret@example.com").doesNotContain("Calle Falsa");
    }

    @Test
    void missingStartDateBecomesVisibleTodo() throws IOException {
        Profile p = importer.importProfile(zip(export()));

        assertThat(p.education().getFirst().highlights()).anyMatch(h -> h.startsWith("TODO") && h.contains("start date"));
    }

    @Test
    void skillsAreDeduplicatedAndClassified() throws IOException {
        Profile p = importer.importProfile(zip(export()));

        assertThat(p.skills()).extracting("name")
                .containsExactly("Java", "Apache Kafka", "Docker", "Liderazgo de equipos", "Maintenance");
        assertThat(p.skills()).extracting("area")
                .containsExactly(Area.BACKEND, Area.MESSAGING, Area.DEVOPS, Area.LEADERSHIP, Area.BACKEND);
    }

    @Test
    void filesInsideFoldersAreFound() throws IOException {
        Map<String, String> nested = new LinkedHashMap<>();
        export().forEach((k, v) -> nested.put("export/" + k, v));

        assertThat(importer.importProfile(zip(nested)).person().name()).isEqualTo("Adán Sánchez");
    }

    @Test
    void rejectsZipThatIsNotALinkedInExport() throws IOException {
        Path other = zip(Map.of("readme.txt", "hello"));

        assertThatThrownBy(() -> importer.importProfile(other))
                .isInstanceOf(ProfileValidationException.class)
                .hasMessageContaining("not a LinkedIn data export");
    }

    @Test
    void rejectsNonZipFiles() throws IOException {
        Path notZip = tmp.resolve("export.zip");
        Files.writeString(notZip, "plain text");

        assertThatThrownBy(() -> importer.importProfile(notZip)).isInstanceOf(ProfileValidationException.class);
    }

    @Test
    void rejectsOversizedEntries() throws IOException {
        Map<String, String> files = export();
        files.put("Positions.csv", "Company Name\n" + "x".repeat(LinkedInExportImporter.MAX_ENTRY_BYTES + 10));

        Path big = zip(files);
        assertThatThrownBy(() -> importer.importProfile(big))
                .isInstanceOf(ProfileValidationException.class)
                .hasMessageContaining("MiB");
    }
}
