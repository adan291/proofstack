package dev.proofstack.adapter.in.studio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.domain.readiness.PublishReadiness;
import dev.proofstack.core.port.in.EditProfileUseCase;
import dev.proofstack.core.port.out.Artifact;

/** Real server on a free port, raw HTTP over a socket so that forged Host and Origin headers can be sent. */
class StudioServerTest {

    /** Editor backed by memory: "INVALID" in the body is a validation error, version "v0" is stale. */
    private static class FakeEditor implements EditProfileUseCase {
        String json = "{\"person\":{\"name\":\"Ada\",\"headline\":\"Dev\"}}";
        int version = 1;
        final List<String> saved = new ArrayList<>();

        @Override
        public Snapshot load() {
            return new Snapshot(json, "v" + version, List.of(new PublishReadiness.Finding(
                    PublishReadiness.Severity.ERROR, "$.person.summary", "placeholder", "write it")), List.of());
        }

        @Override
        public SaveResult save(String body, String expectedVersion) {
            if (body.contains("INVALID")) {
                return new Invalid(List.of(new Violation("$.person.name", "must not be blank")));
            }
            if (!expectedVersion.equals("v" + version)) {
                return new Conflict("v" + version);
            }
            json = body;
            saved.add(body);
            return new Saved("v" + (++version), List.of());
        }
    }

    private final FakeEditor editor = new FakeEditor();
    private final Profile profile = new Profile(new PersonalInfo("Ada", "Dev", null, null, null, List.of()),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    private StudioServer server;
    private String host;
    private String origin;

    @BeforeEach
    void start() {
        server = new StudioServer(editor, () -> profile,
                p -> List.of(new Artifact("index.html", "<h1>" + p.person().name() + "</h1>"), new Artifact("styles.css", "h1{}")),
                p -> List.of(new Artifact("cv.html", "<h1>CV</h1>"), Artifact.binary("cv.docx", new byte[]{'P', 'K'})),
                "{\"title\":\"Proofstack profile\"}", 0);
        server.start();
        host = "127.0.0.1:" + server.port();
        origin = "http://" + host;
    }

    @AfterEach
    void stop() {
        server.close();
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void servesTheProfileWithItsVersionAndFindings() throws IOException {
        Response r = request("GET", "/api/profile", Map.of("Host", host, "X-Studio-Token", server.token()), null);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.header("etag")).isEqualTo("\"v1\"");
        assertThat(r.body).contains("\"document\":{\"person\":{\"name\":\"Ada\"").contains("\"version\":\"v1\"")
                .contains("\"path\":\"$.person.summary\"").contains("\"severity\":\"error\"");
        assertThat(r.header("content-security-policy")).contains("default-src 'self'");
        assertThat(r.header("x-content-type-options")).isEqualTo("nosniff");
        assertThat(r.header("cache-control")).isEqualTo("no-store");
    }

    @Test
    void savesAValidDocument() throws IOException {
        Response r = put("{\"person\":{\"name\":\"Grace\"}}", "\"v1\"");

        assertThat(r.status).as(r.body).isEqualTo(200);
        assertThat(r.body).contains("\"version\":\"v2\"");
        assertThat(editor.saved).containsExactly("{\"person\":{\"name\":\"Grace\"}}");
    }

    @Test
    void servesTheSchemaTheEditorAndThePreview() throws IOException {
        Map<String, String> h = Map.of("Host", host, "X-Studio-Token", server.token());
        assertThat(request("GET", "/api/schema", h, null).body).contains("Proofstack profile");

        Response index = request("GET", "/", Map.of("Host", host), null);
        assertThat(index.status).isEqualTo(200);
        assertThat(index.body).contains("Proofstack Studio");
        assertThat(index.header("content-security-policy")).contains("frame-ancestors 'none'");

        Response site = request("GET", "/preview/site/", Map.of("Host", host), null);
        assertThat(site.status).isEqualTo(200);
        assertThat(site.body).isEqualTo("<h1>Ada</h1>");
        assertThat(site.header("cross-origin-resource-policy")).isEqualTo("cross-origin");
        assertThat(index.header("cross-origin-resource-policy")).isEqualTo("same-origin");
        assertThat(request("GET", "/preview/site/styles.css", Map.of("Host", host), null).header("content-type"))
                .startsWith("text/css");
        assertThat(request("GET", "/preview/cv/", Map.of("Host", host), null).body).isEqualTo("<h1>CV</h1>");
        assertThat(request("GET", "/preview/cv/cv.docx", Map.of("Host", host), null).header("content-type"))
                .contains("wordprocessingml");
    }

    @Test
    void theUrlCarriesTheTokenInTheFragmentOnly() {
        assertThat(server.url().toString()).isEqualTo("http://127.0.0.1:" + server.port() + "/#token=" + server.token());
        assertThat(server.url().getRawQuery()).isNull();
        assertThat(server.token()).hasSizeGreaterThanOrEqualTo(43);
    }

    // ------------------------------------------------------------------ validation and conflicts

    @Test
    void anInvalidDocumentIs422WithItsViolations() throws IOException {
        Response r = put("{\"INVALID\":true}", "\"v1\"");

        assertThat(r.status).isEqualTo(422);
        assertThat(r.body).contains("\"path\":\"$.person.name\"").contains("nothing was saved");
        assertThat(editor.saved).isEmpty();
    }

    @Test
    void aStaleVersionIs409AndSavingWithoutAVersionIs428() throws IOException {
        Response stale = put("{}", "\"v0\"");
        assertThat(stale.status).isEqualTo(409);
        assertThat(stale.body).contains("\"version\":\"v1\"");

        Response missing = request("PUT", "/api/profile", Map.of("Host", host, "Origin", origin,
                "X-Studio-Token", server.token(), "Content-Type", "application/json"), "{}");
        assertThat(missing.status).isEqualTo(428);
        assertThat(editor.saved).isEmpty();
    }

    // ------------------------------------------------------------------ attacks

    @Test
    void apiCallsWithoutTheRightTokenAre401() throws IOException {
        assertThat(request("GET", "/api/profile", Map.of("Host", host), null).status).isEqualTo(401);
        assertThat(request("GET", "/api/profile", Map.of("Host", host, "X-Studio-Token", "guess"), null).status)
                .isEqualTo(401);
    }

    @Test
    void aForeignHostIsRefusedEvenWithTheToken() throws IOException {
        // DNS rebinding: evil.example resolves to 127.0.0.1, the browser sends Host: evil.example
        for (String forged : List.of("evil.example:" + server.port(), "127.0.0.1", "127.0.0.1:1")) {
            Response r = request("GET", "/api/profile", Map.of("Host", forged, "X-Studio-Token", server.token()), null);
            assertThat(r.status).as(forged).isEqualTo(403);
        }
        assertThat(request("GET", "/", Map.of("Host", "evil.example"), null).status).isEqualTo(403);
    }

    @Test
    void writesFromAnotherOriginOrNotInJsonAreRefused() throws IOException {
        Map<String, String> base = new LinkedHashMap<>(Map.of("Host", host, "X-Studio-Token", server.token(),
                "If-Match", "\"v1\"", "Content-Type", "application/json"));
        Map<String, String> foreign = new LinkedHashMap<>(base);
        foreign.put("Origin", "https://evil.example");
        assertThat(request("PUT", "/api/profile", foreign, "{}").status).isEqualTo(403);
        assertThat(request("PUT", "/api/profile", base, "{}").status).as("no Origin").isEqualTo(403);

        Map<String, String> form = new LinkedHashMap<>(base);
        form.put("Origin", origin);
        form.put("Content-Type", "application/x-www-form-urlencoded");
        assertThat(request("PUT", "/api/profile", form, "a=b").status).isEqualTo(415);
        assertThat(editor.saved).isEmpty();
    }

    @Test
    void aBodyOverOneMebibyteIs413() throws IOException {
        // Declares 2 MiB without sending them: the server must refuse from the header, before reading anything.
        Response r = request("PUT", "/api/profile", Map.of("Host", host, "Origin", origin,
                "X-Studio-Token", server.token(), "Content-Type", "application/json", "If-Match", "\"v1\"",
                "Content-Length", String.valueOf(2 * StudioGuard.MAX_BODY_BYTES)), null);

        assertThat(r.status).isEqualTo(413);
        assertThat(editor.saved).isEmpty();
    }

    @Test
    void onlyAllowListedAssetsAreServedAndNoTraversal() throws IOException {
        for (String path : List.of("/assets/../../proofstack/profile.schema.json", "/assets/StudioServer.class",
                "/assets/%2e%2e/index.html", "/preview/site/../../etc/passwd", "/nope")) {
            assertThat(request("GET", path, Map.of("Host", host), null).status).as(path).isEqualTo(404);
        }
        assertThat(request("DELETE", "/api/profile", Map.of("Host", host, "Origin", origin,
                "X-Studio-Token", server.token(), "Content-Type", "application/json"), "{}").status).isEqualTo(405);
        assertThat(request("POST", "/", Map.of("Host", host, "Origin", origin, "Content-Type", "application/json"),
                "{}").status).isEqualTo(405);
    }

    @Test
    void aFileThatIsNotYamlIs422WithWhereItBreaks() throws IOException {
        try (StudioServer broken = new StudioServer(new FakeEditor() {
            @Override
            public Snapshot load() {
                throw new dev.proofstack.core.domain.ProfileValidationException(
                        new Violation("$", "invalid YAML (line 3, column 7): mapping values are not allowed here"));
            }
        }, () -> profile, p -> List.of(), p -> List.of(), "{}", 0)) {
            broken.start();
            Response r = request(broken.port(), "GET", "/api/profile",
                    Map.of("Host", "127.0.0.1:" + broken.port(), "X-Studio-Token", broken.token()), null);
            assertThat(r.status).isEqualTo(422);
            assertThat(r.body).contains("fix it in a text editor").contains("line 3, column 7");
        }
    }

    @Test
    void errorsNeverLeakInternals() throws IOException {
        try (StudioServer broken = new StudioServer(new FakeEditor() {
            @Override
            public Snapshot load() {
                throw new IllegalStateException("C:\\Users\\secret\\profile.yaml exploded");
            }
        }, () -> profile, p -> List.of(), p -> List.of(), "{}", 0)) {
            broken.start();
            Response r = request(broken.port(), "GET", "/api/profile",
                    Map.of("Host", "127.0.0.1:" + broken.port(), "X-Studio-Token", broken.token()), null);
            assertThat(r.status).isEqualTo(500);
            assertThat(r.body).isEqualTo("{\"error\":\"internal error\"}");
        }
    }

    // ------------------------------------------------------------------ raw HTTP client

    private Response put(String body, String ifMatch) throws IOException {
        return request("PUT", "/api/profile", Map.of("Host", host, "Origin", origin, "X-Studio-Token", server.token(),
                "Content-Type", "application/json", "If-Match", ifMatch), body);
    }

    private Response request(String method, String path, Map<String, String> headers, String body) throws IOException {
        return request(server.port(), method, path, headers, body);
    }

    private static Response request(int port, String method, String path, Map<String, String> headers, String body)
            throws IOException {
        byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        StringBuilder head = new StringBuilder(method + " " + path + " HTTP/1.1\r\n");
        headers.forEach((k, v) -> head.append(k).append(": ").append(v).append("\r\n"));
        if (body != null) {
            head.append("Content-Length: ").append(payload.length).append("\r\n");
        }
        head.append("Connection: close\r\n\r\n");
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
            try {
                out.write(payload);
                out.flush();
            } catch (IOException e) {
                // the server may answer 413 and close before reading a huge body
            }
            return Response.parse(socket.getInputStream());
        }
    }

    private record Response(int status, Map<String, String> headers, String body) {

        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        /** Reads the head, then exactly Content-Length bytes, like a browser: never waits for the socket to close. */
        static Response parse(InputStream in) throws IOException {
            ByteArrayOutputStream headBytes = new ByteArrayOutputStream();
            int matched = 0;
            while (matched < 4) {
                int b = in.read();
                if (b < 0) {
                    throw new IOException("connection closed before the response head");
                }
                headBytes.write(b);
                matched = (b == "\r\n\r\n".charAt(matched)) ? matched + 1 : (b == '\r' ? 1 : 0);
            }
            String[] lines = headBytes.toString(StandardCharsets.ISO_8859_1).strip().split("\r\n");
            int status = Integer.parseInt(lines[0].split(" ")[1]);
            Map<String, String> headers = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                headers.put(lines[i].substring(0, colon).strip().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).strip());
            }
            String length = headers.get("content-length");
            byte[] body;
            if (length != null) {
                body = in.readNBytes(Integer.parseInt(length));
            } else {
                ByteArrayOutputStream rest = new ByteArrayOutputStream();
                in.transferTo(rest);
                body = rest.toByteArray();
            }
            String text = new String(body, StandardCharsets.UTF_8);
            if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
                text = dechunk(text);
            }
            return new Response(status, headers, text);
        }
        private static String dechunk(String body) {
            StringBuilder out = new StringBuilder();
            int pos = 0;
            while (pos < body.length()) {
                int eol = body.indexOf("\r\n", pos);
                int size = Integer.parseInt(body.substring(pos, eol).strip(), 16);
                if (size == 0) {
                    break;
                }
                out.append(body, eol + 2, eol + 2 + size);
                pos = eol + 2 + size + 2;
            }
            return out.toString();
        }
    }
}
