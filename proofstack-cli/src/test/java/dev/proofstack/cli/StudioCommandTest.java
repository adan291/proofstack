package dev.proofstack.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.proofstack.adapter.in.studio.StudioServer;
import dev.proofstack.adapter.in.yaml.YamlProfileSource;

/** Studio wired for real: HTTP in, YAML on disk out, through the core. */
class StudioCommandTest {

    private static final Path EXAMPLE = Path.of(System.getProperty("proofstack.repoRoot", ".."))
            .resolve("profiles/example/profile.yaml");

    @TempDir
    Path tmp;

    private Path file;
    private StudioServer server;
    private String base;
    private String token;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws IOException {
        file = tmp.resolve("profile.yaml");
        Files.copy(EXAMPLE, file);
        server = StudioCommand.start(file, 0);
        base = "http://127.0.0.1:" + server.port();
        token = server.url().getFragment().substring("token=".length());
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void editsTheFileThroughTheApiAndKeepsABackup() throws Exception {
        HttpResponse<String> loaded = send(HttpRequest.newBuilder(URI.create(base + "/api/profile"))
                .header("X-Studio-Token", token).GET());
        assertThat(loaded.statusCode()).isEqualTo(200);
        String version = field(loaded.body(), "version");
        String document = documentOf(loaded.body());
        String original = Files.readString(file);

        String edited = document.replaceFirst("\"headline\":\"[^\"]*\"", "\"headline\":\"Staff Engineer\"");
        HttpResponse<String> saved = put(edited, version);

        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        assertThat(new YamlProfileSource().load(file).person().headline()).isEqualTo("Staff Engineer");
        assertThat(Files.readString(tmp.resolve("profile.yaml.bak"))).isEqualTo(original);
    }

    @Test
    void anInvalidEditLeavesTheFileUntouched() throws Exception {
        HttpResponse<String> loaded = send(HttpRequest.newBuilder(URI.create(base + "/api/profile"))
                .header("X-Studio-Token", token).GET());
        String before = Files.readString(file);
        String broken = documentOf(loaded.body()).replaceFirst("\"name\":\"[^\"]*\"", "\"name\":\"\"");

        HttpResponse<String> saved = put(broken, field(loaded.body(), "version"));

        assertThat(saved.statusCode()).isEqualTo(422);
        assertThat(saved.body()).contains("$.person.name");
        assertThat(Files.readString(file)).isEqualTo(before);
    }

    @Test
    void previewsTheSavedProfileAndServesTheEditor() throws Exception {
        assertThat(send(HttpRequest.newBuilder(URI.create(base + "/preview/cv/")).GET()).body()).contains("<h1>");
        assertThat(send(HttpRequest.newBuilder(URI.create(base + "/preview/site/")).GET()).body())
                .contains("<!DOCTYPE html>");
        for (String asset : new String[]{"/", "/assets/studio.js", "/assets/schema-form.js", "/assets/studio.css"}) {
            assertThat(send(HttpRequest.newBuilder(URI.create(base + asset)).GET()).statusCode()).as(asset).isEqualTo(200);
        }
        assertThat(send(HttpRequest.newBuilder(URI.create(base + "/api/schema")).header("X-Studio-Token", token).GET())
                .body()).contains("\"$defs\"");
    }

    private HttpResponse<String> put(String json, String version) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + "/api/profile"))
                .header("X-Studio-Token", token)
                .header("Origin", base)
                .header("Content-Type", "application/json")
                .header("If-Match", "\"" + version + "\"")
                .PUT(HttpRequest.BodyPublishers.ofString(json)));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(json);
        assertThat(m.find()).as(name).isTrue();
        return m.group(1);
    }

    /** The "document" object of the GET response, by brace matching (strings in it never hold braces here). */
    private static String documentOf(String body) {
        int start = body.indexOf("\"document\":") + "\"document\":".length();
        int depth = 0;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return body.substring(start, i + 1);
        }
        throw new AssertionError("no document in " + body);
    }
}
