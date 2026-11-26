package dev.proofstack.adapter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


/**
 * The exact bytes both YAML writers produce today (spec proofstack-yaml-codec, R2.1). A refactoring of the codec must
 * keep them. Regenerate on purpose only: {@code mvn test -Dgolden.update=true}, then review the diff.
 */
class YamlBytesCharacterizationTest {

    private static final Path REPO = Path.of("../..");
    private static final Path GOLDEN = Path.of("src/test/resources/golden");
    private static final Map<String, Path> INPUTS = Map.of(
            "example", REPO.resolve("profiles/example/profile.yaml"),
            "hand-written", REPO.resolve("proofstack-cli/src/test/resources/fixtures/hand-written-profile.yaml"),
            "content", REPO.resolve("proofstack-cli/src/test/resources/fixtures/content-profile.yaml"));

    @TempDir
    Path tmp;

    @Test
    void bothWritersProduceTheSameBytesAsBefore() throws IOException {
        for (var input : INPUTS.entrySet()) {
            String name = input.getKey();

            Path written = tmp.resolve(name + ".writer.yaml");
            new YamlProfileWriter().write(new YamlProfileSource().load(input.getValue()), written, false);

            Path saved = tmp.resolve(name + ".store.yaml");
            Files.copy(input.getValue(), saved);
            var store = new YamlProfileDocumentStore(saved);
            var stored = store.read();
            store.replace(stored.json(), stored.version());

            check(name + ".writer.yaml", written);
            check(name + ".store.yaml", saved);
        }
    }

    private static void check(String golden, Path actual) throws IOException {
        Path expected = GOLDEN.resolve(golden);
        if (Boolean.getBoolean("golden.update")) {
            Files.createDirectories(GOLDEN);
            Files.copy(actual, expected, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        assertThat(new String(Files.readAllBytes(actual), StandardCharsets.UTF_8)).as(golden)
                .isEqualTo(new String(Files.readAllBytes(expected), StandardCharsets.UTF_8));
    }
}
