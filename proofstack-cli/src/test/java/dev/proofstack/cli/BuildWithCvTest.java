package dev.proofstack.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code build --with-cv}: one command publishes the site and its CV, linked from the hero. */
class BuildWithCvTest {

    private static final Path REPO = Path.of(System.getProperty("proofstack.repoRoot", ".."));
    private static final Path EXAMPLE = REPO.resolve("profiles/example/profile.yaml");

    @TempDir
    Path tmp;

    private StringWriter err = new StringWriter();

    private int run(String... args) {
        err = new StringWriter();
        return ProofstackCli.run(args, new PrintWriter(new StringWriter(), true), new PrintWriter(err, true));
    }

    @Test
    void publishesTheCvNextToTheSiteAndLinksIt() throws IOException {
        Path out = tmp.resolve("dist");

        assertThat(run("build", EXAMPLE.toString(), "--out", out.toString(), "--with-cv")).as(err.toString()).isZero();

        assertThat(out.resolve("index.html")).isRegularFile();
        assertThat(out.resolve("cv/cv.html")).isRegularFile();
        assertThat(out.resolve("cv/cv.md")).isRegularFile();
        assertThat(out.resolve("cv/cv.css")).isRegularFile();
        assertThat(Files.readString(out.resolve("index.html"))).contains("href=\"cv/cv.html\"");
    }

    @Test
    void theLinkCanPointToAPdfAddedLater() throws IOException {
        Path out = tmp.resolve("dist");

        assertThat(run("build", EXAMPLE.toString(), "--out", out.toString(), "--with-cv", "--cv-link", "cv/cv.pdf"))
                .as(err.toString()).isZero();

        assertThat(Files.readString(out.resolve("index.html"))).contains("href=\"cv/cv.pdf\"");
    }

    @Test
    void withoutTheFlagThereIsNoCv() throws IOException {
        Path out = tmp.resolve("dist");

        assertThat(run("build", EXAMPLE.toString(), "--out", out.toString())).isZero();

        assertThat(out.resolve("cv")).doesNotExist();
        assertThat(Files.readString(out.resolve("index.html"))).doesNotContain("hero__cv");
    }

    @Test
    void anUnsafeCvLinkIsRejected() {
        assertThat(run("build", EXAMPLE.toString(), "--out", tmp.toString(), "--with-cv", "--cv-link",
                "https://evil.example/cv.pdf")).isEqualTo(1);
        assertThat(err.toString()).contains("relative path");
    }
}
