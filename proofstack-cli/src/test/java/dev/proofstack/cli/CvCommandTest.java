package dev.proofstack.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code proofstack cv}: files, tailoring options and the honest reporting of gaps. */
class CvCommandTest {

    private static final Path REPO = Path.of(System.getProperty("proofstack.repoRoot", ".."));
    private static final Path EXAMPLE = REPO.resolve("profiles/example/profile.yaml");

    @TempDir
    Path tmp;

    private StringWriter out = new StringWriter();
    private StringWriter err = new StringWriter();

    private int run(String... args) {
        out = new StringWriter();
        err = new StringWriter();
        return ProofstackCli.run(args, new PrintWriter(out, true), new PrintWriter(err, true));
    }

    @Test
    void writesMarkdownHtmlAndCss() throws IOException {
        Path dir = tmp.resolve("cv");

        assertThat(run("cv", EXAMPLE.toString(), "--out", dir.toString())).as(err.toString()).isZero();

        assertThat(dir.resolve("cv.css")).isRegularFile();
        assertThat(Files.readAllBytes(dir.resolve("cv.docx"))).startsWith((byte) 0x50, (byte) 0x4B); // "PK": a ZIP
        assertThat(Files.readString(dir.resolve("cv.md")))
                .contains("# ")
                .contains("## Certifications")
                .contains("## Languages")
                .contains("- English (C1)");
        assertThat(Files.readString(dir.resolve("cv.html"))).contains("<h2>Languages</h2>");
    }

    @Test
    void aJobPostingReportsMatchesAndGapsWithoutChangingTheFacts() throws IOException {
        Path posting = tmp.resolve("oferta.txt");
        Files.writeString(posting, "We use Kafka and Kubernetes. Kubernetes experience is a must.");
        Path dir = tmp.resolve("cv");

        assertThat(run("cv", EXAMPLE.toString(), "--out", dir.toString(), "--job", posting.toString()))
                .as(err.toString()).isZero();

        assertThat(out.toString()).contains("Matches the posting:").contains("kafka")
                .contains("profile never says").contains("kubernetes");
        assertThat(Files.readString(dir.resolve("cv.md"))).doesNotContainIgnoringCase("kubernetes");
    }

    @Test
    void unknownFocusIsRejectedWithTheValidValues() {
        assertThat(run("cv", EXAMPLE.toString(), "--out", tmp.toString(), "--focus", "frontend")).isEqualTo(1);
        assertThat(err.toString()).contains("Unknown area 'frontend'").contains("backend");
    }

    @Test
    void emptyJobPostingIsRejected() throws IOException {
        Path posting = tmp.resolve("vacia.txt");
        Files.writeString(posting, "   ");

        assertThat(run("cv", EXAMPLE.toString(), "--out", tmp.toString(), "--job", posting.toString())).isEqualTo(1);
        assertThat(err.toString()).contains("empty");
    }

    @Test
    void missingProfileIsAnEnvironmentError() {
        assertThat(run("cv", tmp.resolve("nope.yaml").toString())).isEqualTo(2);
        assertThat(err.toString()).contains("File not found");
    }
}
