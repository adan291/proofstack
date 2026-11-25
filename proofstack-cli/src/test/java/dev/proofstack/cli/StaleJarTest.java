package dev.proofstack.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** After a git pull, ./proofstack must not silently run the jar built from the old code. */
class StaleJarTest {

    @TempDir
    Path repo;

    private Path file(String path, Instant modified) throws IOException {
        Path p = repo.resolve(path);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "x");
        Files.setLastModifiedTime(p, FileTime.from(modified));
        return p;
    }

    @Test
    void aSourceNewerThanTheBuiltJarIsReported() throws IOException {
        Instant built = Instant.parse("2026-11-01T10:00:00Z");
        Path jar = file("proofstack-cli/target/proofstack.jar", built);
        file("pom.xml", built.minusSeconds(60));
        file("proofstack-core/src/main/java/A.java", built.minusSeconds(60));
        assertThat(ProofstackCli.newerSource(jar)).isEmpty();

        Path changed = file("adapters/proofstack-out-cv/src/main/resources/cv/cv.mustache", built.plusSeconds(60));

        assertThat(ProofstackCli.newerSource(jar)).contains(changed);
    }

    @Test
    void aJarOutsideABuildTreeIsNeverChecked() throws IOException {
        Instant built = Instant.parse("2026-11-01T10:00:00Z");
        Path downloaded = file("proofstack.jar", built);
        file("proofstack-core/src/main/java/A.java", built.plusSeconds(60));

        assertThat(ProofstackCli.newerSource(downloaded)).isEmpty();
    }
}
