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
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end tests of `generate` (against a fake Ollama) and `import linkedin`. */
class ContentCommandsTest {

    private static final Path REPO = Path.of(System.getProperty("proofstack.repoRoot", ".."));
    private static final String PROFILE = Path.of("src/test/resources/fixtures/content-profile.yaml").toString();

    @TempDir
    Path tmp;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private HttpServer ollama;
    private volatile String ollamaAnswer;

    @BeforeEach
    void startFakeOllama() throws IOException {
        ollama = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ollama.createContext("/api/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = ("{\"message\":{\"role\":\"assistant\",\"content\":" + jsonString(ollamaAnswer) + "}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        ollama.start();
    }

    @AfterEach
    void stop() {
        ollama.stop(0);
    }

    private int run(Map<String, String> env, String... args) {
        return ProofstackCli.run(args, new PrintWriter(out, true), new PrintWriter(err, true), env);
    }

    private Map<String, String> ollamaEnv() {
        return Map.of("OLLAMA_HOST", "http://127.0.0.1:" + ollama.getAddress().getPort());
    }

    @Test
    void generatesLinkedInPostAndFlagsInventedNumbers() {
        ollamaAnswer = "{\"title\":\"Tomcat 10\",\"body\":\"Migré de Tomcat 9 a Tomcat 10 y bajé errores un 42%.\",\"hashtags\":[\"java\"]}";

        int code = run(ollamaEnv(), "generate", "linkedin-post", PROFILE, "--subject", "tomcat-jakarta-migration");

        assertThat(code).as(err.toString()).isZero();
        assertThat(out.toString()).startsWith("# Tomcat 10").contains("#java");
        assertThat(err.toString()).contains("WARNING '42%'");
    }

    @Test
    void writesDraftToFileAndRefusesToOverwrite() throws IOException {
        ollamaAnswer = "{\"title\":\"Proofstack\",\"body\":\"## Problem\\nPortfolios sin pruebas.\",\"hashtags\":[]}";
        Path draft = tmp.resolve("README.draft.md");

        assertThat(run(ollamaEnv(), "generate", "readme", PROFILE, "-s", "proofstack", "-o", draft.toString())).isZero();
        assertThat(Files.readString(draft)).startsWith("# Proofstack");

        assertThat(run(ollamaEnv(), "generate", "readme", PROFILE, "-s", "proofstack", "-o", draft.toString())).isEqualTo(1);
        assertThat(err.toString()).contains("--force");
    }

    @Test
    void unknownSubjectAndFormatAreUserErrors() {
        assertThat(run(ollamaEnv(), "generate", "linkedin-post", PROFILE, "-s", "nope")).isEqualTo(1);
        assertThat(err.toString()).contains("Available:").contains("proofstack");

        assertThat(run(ollamaEnv(), "generate", "tweet", PROFILE, "-s", "proofstack")).isEqualTo(1);
        assertThat(err.toString()).contains("linkedin-post");
    }

    @Test
    void unavailableProviderIsEnvironmentErrorWithoutFakeContent() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }

        int code = run(Map.of("OLLAMA_HOST", "127.0.0.1:" + closedPort),
                "generate", "cv-bullets", PROFILE, "-s", "proofstack");

        assertThat(code).isEqualTo(2);
        assertThat(out.toString()).isEmpty();
        assertThat(err.toString()).contains("ollama serve");
    }

    @Test
    void geminiWithoutKeyExplainsHowToConfigure() {
        assertThat(run(Map.of(), "generate", "readme", PROFILE, "-s", "proofstack", "-p", "gemini")).isEqualTo(2);
        assertThat(err.toString()).contains("GEMINI_API_KEY");
    }

    @Test
    void importLinkedInThenValidateAndBuild() throws IOException {
        Path zip = tmp.resolve("export.zip");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(z, "Profile.csv", "First Name,Last Name,Headline,Summary,Geo Location,Websites\n"
                    + "Ada,Lovelace,Backend Engineer,,London,\n");
            entry(z, "Positions.csv", "Company Name,Title,Description,Location,Started On,Finished On\n"
                    + "ACME,Developer,Built APIs,,Jan 2021,\n");
            entry(z, "Skills.csv", "Name\nJava\nKafka\n");
        }
        Path profile = tmp.resolve("ada/profile.yaml");

        assertThat(run(Map.of(), "import", "linkedin", zip.toString(), "--out", profile.toString()))
                .as(err.toString()).isZero();
        assertThat(out.toString()).contains("1 experience", "2 skills", "TODO placeholders");

        assertThat(run(Map.of(), "validate", profile.toString())).as(err.toString()).isZero();
        assertThat(run(Map.of(), "build", profile.toString(), "--out", tmp.resolve("dist").toString())).isZero();

        // Second import must not clobber the file the user may have edited.
        assertThat(run(Map.of(), "import", "linkedin", zip.toString(), "--out", profile.toString())).isEqualTo(1);
    }

    @Test
    void importGitHubWritesNewProfileWithNewRepos() throws IOException {
        HttpServer github = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        github.createContext("/users/jane/repos", exchange -> {
            byte[] body = ("[{\"name\":\"billing-events\",\"html_url\":\"https://github.com/jane/billing-events\"},"
                    + "{\"name\":\"esp32-coffee\",\"description\":\"Coffee via MQTT\",\"language\":\"C++\","
                    + "\"html_url\":\"https://github.com/jane/esp32-coffee\"}]").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        github.start();
        try {
            Path original = REPO.resolve("profiles/example/profile.yaml");
            byte[] before = Files.readAllBytes(original);
            Path out = tmp.resolve("profile.github.yaml");
            Map<String, String> env = Map.of("PROOFSTACK_GITHUB_API", "http://127.0.0.1:" + github.getAddress().getPort());

            int code = run(env, "import", "github", "jane", "--profile", original.toString(), "--out", out.toString());

            assertThat(code).as(err.toString()).isZero();
            assertThat(out.toString()).isNotEmpty();
            assertThat(this.out.toString()).contains("+ esp32-coffee").contains("Already in your profile: billing-events");
            assertThat(run(Map.of(), "validate", out.toString())).as(err.toString()).isZero();
            assertThat(Files.readAllBytes(original)).isEqualTo(before);

            assertThat(run(env, "import", "github", "jane", "-p", original.toString(), "-o", original.toString()))
                    .isEqualTo(1);
            assertThat(run(env, "import", "github", "nobody", "-p", original.toString(), "-o",
                    tmp.resolve("x.yaml").toString())).isEqualTo(1);
            assertThat(err.toString()).contains("not found");
        } finally {
            github.stop(0);
        }
    }

    private static void entry(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
