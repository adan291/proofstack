package dev.proofstack.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The "Use this template" journey: init → doctor (blocked) → fill in → doctor (ok) → build. */
class TemplateCommandsTest {

    private static final Path REPO = Path.of(System.getProperty("proofstack.repoRoot", ".."));

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
    void initCreatesValidStarterThatDoctorRefusesToPublish() throws IOException {
        Path dir = tmp.resolve("profiles/ada");

        assertThat(run("init", dir.toString(), "--name", "Ada Lovelace", "--headline", "Backend Engineer",
                "--language", "es")).as(err.toString()).isZero();
        Path profile = dir.resolve("profile.yaml");
        assertThat(Files.readString(profile)).contains("language: es").contains("name: Ada Lovelace");

        assertThat(run("validate", profile.toString())).isZero();
        assertThat(run("doctor", profile.toString())).isEqualTo(1);
        assertThat(out.toString()).contains("ERROR $.person.summary").contains("Not ready to publish");

        assertThat(run("init", dir.toString(), "-n", "Ada", "-t", "Backend")).isEqualTo(1);
        assertThat(err.toString()).contains("--force");
    }

    @Test
    void buildWarnsAboutPlaceholdersItIsPublishing() throws IOException {
        Path dir = tmp.resolve("profiles/ada");
        run("init", dir.toString(), "--name", "Ada Lovelace", "--headline", "Backend Engineer");

        assertThat(run("build", dir.resolve("profile.yaml").toString(), "--out", tmp.resolve("dist").toString()))
                .as(err.toString()).isZero();

        assertThat(err.toString()).containsPattern("WARN \\d+ TODO placeholders").contains("proofstack doctor");
    }

    /** Windows consoles use a legacy code page: anything doctor adds around the user's text must be plain ASCII. */
    @Test
    void doctorDecorationsArePlainAscii() throws IOException {
        Path profile = tmp.resolve("profile.yaml");
        Files.writeString(profile, """
                language: en
                person:
                  name: Ada Lovelace
                  headline: "TODO: a headline long enough to be abbreviated by doctor in its message"
                experience:
                  - id: acme
                    role: Developer
                    company: ACME
                    startDate: 2022-01
                """);

        run("doctor", profile.toString());

        assertThat(out.toString()).contains("no summary").contains("no highlights").matches("\\p{ASCII}*");
    }

    @Test
    void completedProfilePassesDoctorAndStrictReportsWarnings() throws IOException {
        Path profile = tmp.resolve("profile.yaml");
        Files.writeString(profile, """
                language: en
                person:
                  name: Ada Lovelace
                  headline: Backend Engineer
                  summary: I build event-driven backends.
                  links:
                    - { label: GitHub, url: "https://github.com/ada" }
                skills:
                  - { name: Kafka, area: messaging }
                projects:
                  - id: billing
                    name: Event-driven billing
                    summary: Billing extracted from a monolith.
                    problem: Every billing change needed a full deploy.
                    solution: Outbox table plus a Kafka consumer.
                    links:
                      - { label: Repo, url: "https://github.com/ada/billing" }
                """);

        assertThat(run("doctor", profile.toString())).as(out.toString()).isZero();
        assertThat(out.toString()).contains("WARN  $.experience").contains("Publishable");
        assertThat(run("doctor", profile.toString(), "--strict")).isEqualTo(1);
    }

    @Test
    void bundledExampleIsBlockedBecauseOfSampleEmail() {
        assertThat(run("doctor", REPO.resolve("profiles/example/profile.yaml").toString())).isEqualTo(1);
        assertThat(out.toString()).contains("ERROR $.person.email");
    }

    @Test
    void invalidProfileIsReportedAsSuch() throws IOException {
        Path profile = tmp.resolve("bad.yaml");
        Files.writeString(profile, "person: { headline: x }\n");

        assertThat(run("doctor", profile.toString())).isEqualTo(1);
        assertThat(err.toString()).contains("not valid yet");
    }
}
