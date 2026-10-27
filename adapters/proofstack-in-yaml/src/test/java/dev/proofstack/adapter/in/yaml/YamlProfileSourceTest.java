package dev.proofstack.adapter.in.yaml;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.EvidenceCategory;
import dev.proofstack.core.domain.LabType;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlProfileSourceTest {

    private final YamlProfileSource source = new YamlProfileSource();

    @TempDir
    Path dir;

    @Test
    void loadsCompleteProfile() throws IOException {
        Profile profile = source.load(write("""
                language: es
                person:
                  name: Ada Lovelace
                  headline: Backend Engineer
                  email: ada@example.com
                  links:
                    - { label: GitHub, url: "https://github.com/example" }
                skills:
                  - { name: Java, area: backend }
                experience:
                  - id: acme
                    role: Developer
                    company: ACME
                    startDate: 2022-01
                    current: true
                    evidence: [team-lead]
                projects:
                  - id: rag
                    name: RAG
                    summary: Ask my portfolio
                    areas: [ai]
                evidence:
                  - id: team-lead
                    category: leadership
                    title: Coordinated 20 people
                    context: Big project
                    action: Planning and 1:1s
                    result: Delivered on time
                labs:
                  - { id: ai-lab, name: AI Lab, type: ai, description: Experiments, projects: [rag] }
                """));

        assertThat(profile.language()).isEqualTo("es");
        assertThat(profile.person().name()).isEqualTo("Ada Lovelace");
        assertThat(profile.skills().getFirst().area()).isEqualTo(Area.BACKEND);
        assertThat(profile.experience().getFirst().period().start()).isEqualTo(YearMonth.of(2022, 1));
        assertThat(profile.experience().getFirst().current()).isTrue();
        assertThat(profile.evidence().getFirst().category()).isEqualTo(EvidenceCategory.LEADERSHIP);
        assertThat(profile.labs().getFirst().type()).isEqualTo(LabType.AI);
    }

    @Test
    void reportsSchemaErrorsWithPaths() throws IOException {
        Path file = write("""
                person:
                  headline: Backend
                projects:
                  - id: Not Valid Id
                    name: x
                    summary: y
                    areas: [cooking]
                unknownField: true
                """);

        assertThatThrownBy(() -> source.load(file))
                .isInstanceOfSatisfying(ProfileValidationException.class, e ->
                        assertThat(e.violations()).extracting(Violation::path)
                                .contains("$.person", "$.projects[0].id", "$.projects[0].areas[0]", "$"));
    }

    @Test
    void rejectsDangerousLinkScheme() throws IOException {
        Path file = write("""
                person:
                  name: Ada
                  headline: Backend
                  links:
                    - { label: x, url: "javascript:alert(1)" }
                """);

        assertThatThrownBy(() -> source.load(file))
                .isInstanceOfSatisfying(ProfileValidationException.class, e ->
                        assertThat(e.violations()).extracting(Violation::path).contains("$.person.links[0].url"));
    }

    @Test
    void reportsDomainInvariantsWithPaths() throws IOException {
        Path file = write("""
                person: { name: Ada, headline: Backend }
                experience:
                  - { id: a, role: Dev, company: ACME, startDate: 2024-05, endDate: 2024-01 }
                  - { id: b, role: Dev, company: ACME, startDate: 2024-01, endDate: 2024-02, current: true }
                """);

        assertThatThrownBy(() -> source.load(file))
                .isInstanceOfSatisfying(ProfileValidationException.class, e ->
                        assertThat(e.violations()).extracting(Violation::path)
                                .containsExactly("$.experience[0].endDate", "$.experience[1].endDate"));
    }

    @Test
    void reportsDanglingReferences() throws IOException {
        Path file = write("""
                person: { name: Ada, headline: Backend }
                labs:
                  - { id: hw, name: Hardware, type: hardware, description: ESP32, projects: [ghost] }
                """);

        assertThatThrownBy(() -> source.load(file))
                .isInstanceOfSatisfying(ProfileValidationException.class, e ->
                        assertThat(e.violations()).extracting(Violation::path).containsExactly("$.labs[0].projects[0]"));
    }

    @Test
    void reportsYamlSyntaxErrors() throws IOException {
        Path file = write("person: [unclosed");

        assertThatThrownBy(() -> source.load(file))
                .isInstanceOfSatisfying(ProfileValidationException.class, e ->
                        assertThat(e.violations().getFirst().message()).startsWith("invalid YAML"));
    }

    @Test
    void rejectsEmptyDocument() throws IOException {
        Path file = write("");

        assertThatThrownBy(() -> source.load(file))
                .isInstanceOfSatisfying(ProfileValidationException.class, e ->
                        assertThat(e.violations().getFirst().message()).isEqualTo("document is empty"));
    }

    private Path write(String yaml) throws IOException {
        Path file = dir.resolve("profile.yaml");
        Files.writeString(file, yaml);
        return file;
    }
}
