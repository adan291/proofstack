package dev.proofstack.adapter.in.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.port.out.ProfileDocumentStore.Stored;
import dev.proofstack.core.port.out.ProfileDocumentStore.VersionConflict;

class YamlProfileDocumentStoreTest {

    private static final String ORIGINAL = """
            # yaml-language-server: $schema=../../schema/profile.schema.json
            # Notas a mano que deben sobrevivir.

            language: es
            person:
              name: Ada Lovelace
              headline: Backend Engineer
            skills:
              - { name: Java, area: backend }
            """;

    @TempDir
    Path dir;

    private Path file;
    private YamlProfileDocumentStore store;

    @BeforeEach
    void setUp() throws IOException {
        file = dir.resolve("profile.yaml");
        Files.writeString(file, ORIGINAL);
        store = new YamlProfileDocumentStore(file);
    }

    @Test
    void readsTheFileAsJsonWithAStableVersion() {
        Stored stored = store.read();

        assertThat(stored.json()).startsWith("{\"language\":\"es\",\"person\":{\"name\":\"Ada Lovelace\"");
        assertThat(stored.version()).hasSize(64).isEqualTo(store.read().version());
    }

    @Test
    void savesAtomicallyKeepsABackupAndTheHeaderComments() throws IOException {
        Stored before = store.read();
        String edited = before.json().replace("Backend Engineer", "Staff Engineer");

        String version = store.replace(edited, before.version());

        String yaml = Files.readString(file);
        assertThat(yaml).startsWith("# yaml-language-server: $schema=../../schema/profile.schema.json\n"
                + "# Notas a mano que deben sobrevivir.\n\n");
        assertThat(yaml).contains("headline: Staff Engineer").doesNotContain("---");
        assertThat(yaml.indexOf("language:")).isLessThan(yaml.indexOf("person:"));
        assertThat(Files.readString(dir.resolve("profile.yaml.bak"))).isEqualTo(ORIGINAL);
        assertThat(version).isNotEqualTo(before.version()).isEqualTo(store.read().version());
        assertThat(new YamlProfileSource().load(file).person().headline()).isEqualTo("Staff Engineer");
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder("profile.yaml",
                    "profile.yaml.bak");
        }
    }

    @Test
    void refusesToOverwriteAnEditMadeByAnotherProgram() throws IOException {
        Stored before = store.read();
        Files.writeString(file, ORIGINAL.replace("Ada Lovelace", "Grace Hopper"));

        assertThatThrownBy(() -> store.replace(before.json(), before.version()))
                .isInstanceOf(VersionConflict.class);
        assertThat(Files.readString(file)).contains("Grace Hopper");
        assertThat(dir.resolve("profile.yaml.bak")).doesNotExist();
    }

    @Test
    void neverWritesAnInvalidDocument() throws IOException {
        Stored before = store.read();

        assertThatThrownBy(() -> store.replace("{\"person\":{\"name\":\"\"}}", before.version()))
                .isInstanceOf(ProfileValidationException.class);
        assertThatThrownBy(() -> store.replace("not json", before.version()))
                .isInstanceOf(ProfileValidationException.class);
        assertThatThrownBy(() -> store.replace("[1,2]", before.version()))
                .isInstanceOf(ProfileValidationException.class);
        assertThat(Files.readString(file)).isEqualTo(ORIGINAL);
    }

    @Test
    void parseAppliesTheSchemaAndTheDomainRules() {
        assertThatThrownBy(() -> store.parse("{\"person\":{\"name\":\"Ada\",\"headline\":\"Dev\"},\"unknown\":1}"))
                .isInstanceOf(ProfileValidationException.class);
        assertThatThrownBy(() -> store.parse("{\"person\":{\"name\":\"Ada\",\"headline\":\"Dev\"},"
                + "\"labs\":[{\"id\":\"l\",\"name\":\"L\",\"type\":\"ai\",\"description\":\"d\",\"projects\":[\"missing\"]}]}"))
                .isInstanceOf(ProfileValidationException.class);
        assertThat(store.parse("{\"person\":{\"name\":\"Ada\",\"headline\":\"Dev\"}}").person().name()).isEqualTo("Ada");
    }

    @Test
    void theRealProfileSurvivesAReadAndSaveRoundTrip() throws IOException {
        Path real = Path.of("../../profiles/example/profile.yaml");
        Path copy = dir.resolve("example.yaml");
        Files.copy(real, copy);
        YamlProfileDocumentStore example = new YamlProfileDocumentStore(copy);
        var before = new YamlProfileSource().load(copy);

        Stored stored = example.read();
        example.replace(stored.json(), stored.version());

        assertThat(new YamlProfileSource().load(copy)).isEqualTo(before);
    }
}
