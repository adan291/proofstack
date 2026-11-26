package dev.proofstack.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import dev.proofstack.adapter.yaml.YamlProfileSource;
import dev.proofstack.core.domain.Project;

class SetupCommandTest {

    @TempDir
    Path tmp;

    private HttpServer github;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void fakeGitHub() throws IOException {
        github = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        github.createContext("/users/jane/repos", exchange -> {
            byte[] body = ("[{\"name\":\"billing-events\",\"description\":\"Billing\",\"language\":\"Java\","
                    + "\"html_url\":\"https://github.com/jane/billing-events\"}]").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        github.start();
    }

    @AfterEach
    void stop() {
        github.stop(0);
        SetupCommand.input = new BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8));
    }

    private Map<String, String> env() {
        return Map.of("PROOFSTACK_GITHUB_API", "http://127.0.0.1:" + github.getAddress().getPort(),
                "PROOFSTACK_HOME", tmp.resolve("home").toString());
    }

    private int run(String... args) {
        return ProofstackCli.run(args, new PrintWriter(out, true), new PrintWriter(err, true), env());
    }

    @Test
    void createsTheProfileLinksGitHubAndBringsItsRepositories() {
        Path dir = tmp.resolve("profiles/jane");
        int code = run("setup", dir.toString(), "--name", "Jane Doe", "--github", "jane", "--skip-linkedin",
                "--no-studio", "--yes");

        assertThat(code).as(out + "\n" + err).isZero();
        var profile = new YamlProfileSource().load(dir.resolve("profile.yaml"));
        assertThat(profile.person().name()).isEqualTo("Jane Doe");
        assertThat(profile.person().links()).anySatisfy(l -> assertThat(l.url()).isEqualTo("https://github.com/jane"));
        assertThat(profile.projects()).extracting(Project::id).contains("billing-events");
        assertThat(dir.resolve("profile.setup.yaml")).doesNotExist();
        assertThat(out.toString()).contains("1/4 Profile", "2/4 GitHub", "3/4 LinkedIn", "4/4 What is left");
    }

    @Test
    void runningItAgainKeepsTheProfileAndAddsNothingTwice() throws IOException {
        Path dir = tmp.resolve("profiles/jane");
        assertThat(run("setup", dir.toString(), "-n", "Jane Doe", "-g", "jane", "--skip-linkedin", "--no-studio", "-y"))
                .isZero();
        String first = Files.readString(dir.resolve("profile.yaml"));
        // The second time GitHub comes from the profile's own link, without --github.
        assertThat(run("setup", dir.toString(), "--skip-linkedin", "--no-studio", "-y")).as(err.toString()).isZero();
        assertThat(Files.readString(dir.resolve("profile.yaml"))).isEqualTo(first);
        assertThat(out.toString()).contains("Already in your profile: billing-events");
    }

    @Test
    void asksForWhatIsMissingAndUsesANameBasedFolder() {
        SetupCommand.input = new BufferedReader(new StringReader("Jane Doe\nBackend Engineer\njane\nall\n"));
        Path cwdProfiles = Path.of("profiles", "jane-doe", "profile.yaml");
        assertThat(SetupCommand.slug("Adán Sánchez Manzano")).isEqualTo("adan-sanchez-manzano");
        Path dir = tmp.resolve("asked");
        assertThat(run("setup", dir.toString(), "--skip-linkedin", "--no-studio")).as(out + "\n" + err).isZero();
        var profile = new YamlProfileSource().load(dir.resolve("profile.yaml"));
        assertThat(profile.person().headline()).isEqualTo("Backend Engineer");
        assertThat(profile.projects()).extracting(Project::id).contains("billing-events");
        assertThat(cwdProfiles).doesNotExist();
    }

    @Test
    void aHandWrittenProfileKeepsItsBytesWhenNothingIsNewAndItsHeaderWhenSomethingIs() throws IOException {
        Path dir = tmp.resolve("profiles/jane");
        Files.createDirectories(dir);
        Path file = dir.resolve("profile.yaml");
        String handWritten = """
                # My profile. Every number comes from git.
                language: en
                person:
                  name: Jane Doe
                  headline: Backend Engineer
                  links:
                    - { label: GitHub, url: "https://github.com/jane" }   # inline comment
                projects:
                  - id: billing-events
                    name: Billing events
                    summary: Billing
                    areas: [backend]
                    links:
                      - { label: GitHub, url: "https://github.com/jane/billing-events" }
                """;
        Files.writeString(file, handWritten);
        assertThat(run("setup", dir.toString(), "--skip-linkedin", "--no-studio", "-y")).as(err.toString()).isZero();
        assertThat(Files.readString(file)).isEqualTo(handWritten);

        Files.writeString(file, handWritten.replace("billing-events", "other").replace("Billing events", "Other"));
        assertThat(run("setup", dir.toString(), "--skip-linkedin", "--no-studio", "-y")).as(err.toString()).isZero();
        assertThat(Files.readString(file)).startsWith("# My profile. Every number comes from git.\nlanguage: en")
                .contains("billing-events").doesNotContain("Generated by Proofstack");
    }

    @Test
    void repositoriesTurnedDownAreRememberedAndNeverOfferedAgain() throws IOException {
        Path dir = tmp.resolve("profiles/jane");
        // Name, role, GitHub user, then Enter at "which repositories": none.
        SetupCommand.input = new BufferedReader(new StringReader("Jane Doe\n\njane\n\n"));
        assertThat(run("setup", dir.toString(), "--skip-linkedin", "--no-studio")).as(out + "\n" + err).isZero();
        assertThat(new YamlProfileSource().load(dir.resolve("profile.yaml")).projects())
                .extracting(Project::id).doesNotContain("billing-events");
        assertThat(Files.readString(dir.resolve(GitHubIgnore.FILE))).contains("billing-events");

        // A workflow (no questions) and the import command skip it too.
        assertThat(run("setup", dir.toString(), "--skip-linkedin", "--no-studio", "-y")).isZero();
        assertThat(new YamlProfileSource().load(dir.resolve("profile.yaml")).projects())
                .extracting(Project::id).doesNotContain("billing-events");
        Path other = tmp.resolve("other.yaml");
        assertThat(run("import", "github", "jane", "-p", dir.resolve("profile.yaml").toString(), "-o", other.toString()))
                .isZero();
        assertThat(new YamlProfileSource().load(other).projects()).extracting(Project::id).doesNotContain("billing-events");
    }

    @Test
    void refusesWhatCannotBeAGitHubUser() {
        assertThat(run("setup", tmp.resolve("x").toString(), "-n", "Jane", "-g", "jane/../x", "--no-studio", "-y"))
                .isEqualTo(1);
        assertThat(SetupCommand.validGitHubUser("adan291")).isTrue();
        assertThat(SetupCommand.validGitHubUser("-bad")).isFalse();
        assertThat(SetupCommand.gitHubUserOf("https://github.com/adan291/")).isEqualTo("adan291");
        assertThat(SetupCommand.gitHubUserOf("https://github.com/adan291/repo")).isNull();
    }
}
