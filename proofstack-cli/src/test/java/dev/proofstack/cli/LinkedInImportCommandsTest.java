package dev.proofstack.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** "Coger todo de LinkedIn": API import merged into a hand-written profile, which must stay untouched. */
class LinkedInImportCommandsTest {

    /** A hand-written profile with placeholders. Never a real profile: those change and must not break the build. */
    private static final Path HAND_WRITTEN = Path.of("src/test/resources/fixtures/hand-written-profile.yaml");

    @TempDir
    Path tmp;

    private HttpServer linkedIn;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void start() throws IOException {
        Map<String, String> domains = Map.of(
                "PROFILE", "[{\"First Name\":\"Adán\",\"Last Name\":\"Sánchez\",\"Headline\":\"Backend Developer\",\"Summary\":\"\"}]",
                "POSITIONS", "[{\"Company Name\":\"Empresa Real\",\"Title\":\"Backend Developer\","
                        + "\"Description\":\"\",\"Started On\":\"feb 2021\",\"Finished On\":\"\"}]",
                "EDUCATION", "[{\"School Name\":\"IES Real\",\"Degree Name\":\"CFGS DAM\",\"Start Date\":\"2018\",\"End Date\":\"2020\"}]",
                "SKILLS", "[{\"Name\":\"Java\"},{\"Name\":\"Spring Boot\"},{\"Name\":\"PostgreSQL\"}]");
        linkedIn = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        linkedIn.createContext("/rest/memberSnapshotData", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String domain = query.substring(query.indexOf("domain=") + 7);
            String rows = domains.get(domain);
            byte[] body = (rows == null ? "{\"message\":\"No data found for this memberId\"}"
                    : "{\"elements\":[{\"snapshotDomain\":\"" + domain + "\",\"snapshotData\":" + rows + "}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(rows == null ? 404 : 200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        linkedIn.start();
    }

    @AfterEach
    void stop() {
        linkedIn.stop(0);
    }

    private int run(Map<String, String> env, String... args) {
        return ProofstackCli.run(args, new PrintWriter(out, true), new PrintWriter(err, true), env);
    }

    @Test
    void apiImportMergedIntoAHandWrittenProfileFillsCareerAndKeepsProofs() throws IOException {
        byte[] before = Files.readAllBytes(HAND_WRITTEN);
        Path merged = tmp.resolve("profile.linkedin.yaml");
        Map<String, String> env = Map.of("PROOFSTACK_LINKEDIN_API", "http://127.0.0.1:" + linkedIn.getAddress().getPort(),
                "LINKEDIN_ACCESS_TOKEN", "test-token");

        int code = run(env, "import", "linkedin-api", "--merge-into", HAND_WRITTEN.toString(), "--out", merged.toString());

        assertThat(code).as(err.toString()).isZero();
        String yaml = Files.readString(merged);
        assertThat(yaml).contains("company: Empresa Real").contains("institution: IES Real")
                .contains("name: Ada Lovelace")                  // the profile's own name wins
                .contains("tomcat-jakarta-migration")          // projects kept
                .contains("team-coordination")                 // evidence kept and still linked from the job
                .contains("Migración de aplicaciones de Tomcat 9 a Tomcat 10")
                .doesNotContain("TODO: empresa").doesNotContain("TODO: centro")
                .doesNotContain("test-token");
        assertThat(Files.readAllBytes(HAND_WRITTEN)).isEqualTo(before);
        assertThat(run(env, "validate", merged.toString())).as(err.toString()).isZero();

        assertThat(run(env, "import", "linkedin-api", "-m", HAND_WRITTEN.toString(), "-o", HAND_WRITTEN.toString(), "--force"))
                .isEqualTo(1);
    }

    /** A hard link (or a symlink, or another letter case on macOS) is another name for the same file. */
    @Test
    void anotherNameForTheOriginalProfileIsRefusedEvenWithForce() throws IOException {
        Path mine = tmp.resolve("profile.yaml");
        Files.copy(HAND_WRITTEN, mine);
        Path alias = Files.createLink(tmp.resolve("alias.yaml"), mine);
        byte[] before = Files.readAllBytes(mine);
        Map<String, String> env = Map.of("PROOFSTACK_LINKEDIN_API", "http://127.0.0.1:" + linkedIn.getAddress().getPort(),
                "LINKEDIN_ACCESS_TOKEN", "test-token");

        int code = run(env, "import", "linkedin-api", "-m", mine.toString(), "-o", alias.toString(), "--force");

        assertThat(code).as(out.toString()).isEqualTo(1);
        assertThat(err.toString()).contains("different file");
        assertThat(Files.readAllBytes(mine)).isEqualTo(before);
    }

    @Test
    void apiImportWithoutCredentialsExplainsSetup() {
        assertThat(run(Map.of(), "import", "linkedin-api", "--out", tmp.resolve("x.yaml").toString())).isEqualTo(1);
        assertThat(err.toString()).contains("LINKEDIN_CLIENT_ID").contains("docs/linkedin-api.md");
    }
}
