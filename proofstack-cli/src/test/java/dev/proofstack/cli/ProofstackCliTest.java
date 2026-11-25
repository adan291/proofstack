package dev.proofstack.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProofstackCliTest {

    private static final Path REPO = Path.of(System.getProperty("proofstack.repoRoot", ".."));

    @TempDir
    Path tmp;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private int run(String... args) {
        return ProofstackCli.run(args, new PrintWriter(out, true), new PrintWriter(err, true));
    }

    /** The version comes from the pom: bumping it with {@code mvn versions:set} must be enough. */
    @Test
    void versionIsTheOneInThePom() {
        String expected = System.getProperty("proofstack.version");
        assertThat(expected).as("set by surefire from ${project.version}").isNotBlank();

        assertThat(run("--version")).isZero();
        assertThat(out.toString().strip()).isEqualTo("proofstack " + expected);
    }

    @Test
    void bundledProfilesAreValid() {
        assertThat(run("validate", REPO.resolve("profiles/example/profile.yaml").toString())).isZero();
        assertThat(run("validate", REPO.resolve("profiles/adan/profile.yaml").toString()))
                .as(err.toString()).isZero();
    }

    @Test
    void buildGeneratesStaticSiteEndToEnd() throws IOException {
        Path dist = tmp.resolve("nested/dist");

        int code = run("build", REPO.resolve("profiles/example/profile.yaml").toString(), "--out", dist.toString());

        assertThat(code).as(err.toString()).isZero();
        assertThat(dist.resolve("index.html")).exists();
        assertThat(dist.resolve("styles.css")).exists();
        assertThat(dist.resolve("app.js")).exists();
        assertThat(Files.readString(dist.resolve("index.html"))).contains("Built with Proofstack");
    }

    @Test
    void buildWithAssistantUrlAddsWidgetAndRejectsInsecureUrls() throws IOException {
        Path dist = tmp.resolve("with-ai");
        String profile = REPO.resolve("profiles/example/profile.yaml").toString();

        assertThat(run("build", profile, "--out", dist.toString(), "--assistant-url", "https://ai.example.dev"))
                .as(err.toString()).isZero();
        assertThat(Files.readString(dist.resolve("index.html"))).contains("data-endpoint=\"https://ai.example.dev/api/ask\"");

        assertThat(run("build", profile, "--out", tmp.resolve("bad").toString(), "--assistant-url", "http://ai.example.dev"))
                .isEqualTo(1);
        assertThat(err.toString()).contains("https");
    }

    @Test
    void invalidProfileReturnsOneWithPaths() throws IOException {
        Path profile = tmp.resolve("profile.yaml");
        Files.writeString(profile, "person: { headline: Backend }\n");

        assertThat(run("validate", profile.toString())).isEqualTo(1);
        assertThat(err.toString()).contains("$.person");
    }

    @Test
    void invalidProfileDoesNotWriteOutput() throws IOException {
        Path profile = tmp.resolve("profile.yaml");
        Files.writeString(profile, "person: { headline: Backend }\n");
        Path dist = tmp.resolve("dist");

        assertThat(run("build", profile.toString(), "--out", dist.toString())).isEqualTo(1);
        assertThat(dist).doesNotExist();
    }

    @Test
    void missingFileReturnsTwo() {
        assertThat(run("validate", tmp.resolve("nope.yaml").toString())).isEqualTo(2);
        assertThat(err.toString()).contains("File not found");
    }
}
