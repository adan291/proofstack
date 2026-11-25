package dev.proofstack.adapter.in.studio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.domain.readiness.PublishReadiness.Finding;
import dev.proofstack.core.port.in.EditProfileUseCase;
import dev.proofstack.core.port.in.EditProfileUseCase.Conflict;
import dev.proofstack.core.port.in.EditProfileUseCase.Invalid;
import dev.proofstack.core.port.in.EditProfileUseCase.Saved;
import dev.proofstack.core.port.in.EditProfileUseCase.Snapshot;
import dev.proofstack.core.port.out.Artifact;
import dev.proofstack.core.port.out.PortfolioRenderer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Driving adapter: Studio, the local editor of one {@code profile.yaml}.
 *
 * <p>Routes: {@code /} and {@code /assets/*} (the editor, from the classpath), {@code GET /api/schema},
 * {@code GET|PUT /api/profile}, {@code /preview/site/*} and {@code /preview/cv/*} (the saved profile rendered in
 * memory, never written to disk). Listens on the loopback interface only; see {@link StudioGuard} for who may talk
 * to it.
 */
public final class StudioServer implements AutoCloseable {

    private static final String UI = "/studio/";
    private static final Map<String, String> ASSETS = Map.of(
            "index.html", "text/html; charset=utf-8",
            "studio.css", "text/css; charset=utf-8",
            "studio.js", "text/javascript; charset=utf-8",
            "schema-form.js", "text/javascript; charset=utf-8");
    private static final String UI_CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "frame-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
    private static final String PREVIEW_CSP = "default-src 'self'; img-src 'self' data:; frame-ancestors 'self'; "
            + "base-uri 'none'; form-action 'none'";

    private final EditProfileUseCase editor;
    private final Supplier<Profile> saved;
    private final PortfolioRenderer site;
    private final PortfolioRenderer cv;
    private final String schema;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final StudioGuard guard;

    /**
     * @param saved  the profile as it is on disk now, for the preview
     * @param schema the JSON Schema of {@code profile.yaml}, given to the editor so it can build its forms
     * @param port   {@code 0} for any free port
     */
    public StudioServer(EditProfileUseCase editor, Supplier<Profile> saved, PortfolioRenderer site,
                        PortfolioRenderer cv, String schema, int port) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.saved = Objects.requireNonNull(saved, "saved");
        this.site = Objects.requireNonNull(site, "site");
        this.cv = Objects.requireNonNull(cv, "cv");
        this.schema = Objects.requireNonNull(schema, "schema");
        try {
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot listen on 127.0.0.1:" + port + " (is the port in use?)", e);
        }
        this.guard = new StudioGuard(server.getAddress().getPort(), StudioGuard.newToken());
        server.createContext("/", this::handle);
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /** The URL to open. The token travels in the fragment, so it never reaches a server, a log or a Referer. */
    public URI url() {
        return URI.create("http://127.0.0.1:" + port() + "/#token=" + guard.token());
    }

    String token() {
        return guard.token();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------ dispatch

    private void handle(HttpExchange exchange) throws IOException {
        // Not try-with-resources: the exchange must still be open when the catch block answers with an error.
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            var headers = exchange.getRequestHeaders();
            Optional<StudioGuard.Refusal> refusal = guard.check(method, path, headers.getFirst("Host"),
                    headers.getFirst("Origin"), headers.getFirst("Content-Type"),
                    headers.getFirst(StudioGuard.TOKEN_HEADER), contentLength(headers.getFirst("Content-Length")),
                    headers.getFirst("Sec-Fetch-Site"));
            if (refusal.isPresent()) {
                error(exchange, refusal.get().status(), refusal.get().reason());
                return;
            }
            route(exchange, method, path);
        } catch (RuntimeException e) {
            // Never leak a stack trace or an absolute path to the browser.
            error(exchange, 500, e instanceof UncheckedIOException ? "cannot read or write the profile" : "internal error");
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange, String method, String path) throws IOException {
        if (path.equals("/api/profile")) {
            switch (method) {
                case "GET" -> getProfile(exchange);
                case "PUT" -> putProfile(exchange);
                default -> error(exchange, 405, "use GET or PUT");
            }
            return;
        }
        if (!method.equals("GET") && !method.equals("HEAD")) {
            error(exchange, 405, "read-only resource");
            return;
        }
        if (path.equals("/api/schema")) {
            send(exchange, 200, "application/schema+json", schema.getBytes(StandardCharsets.UTF_8), UI_CSP);
        } else if (path.equals("/")) {
            asset(exchange, "index.html");
        } else if (path.startsWith("/assets/")) {
            asset(exchange, path.substring("/assets/".length()));
        } else if (path.startsWith("/preview/site/")) {
            preview(exchange, site, path.substring("/preview/site/".length()), "index.html");
        } else if (path.startsWith("/preview/cv/")) {
            preview(exchange, cv, path.substring("/preview/cv/".length()), "cv.html");
        } else {
            error(exchange, 404, "not found");
        }
    }

    // ------------------------------------------------------------------ API

    private void getProfile(HttpExchange exchange) throws IOException {
        Snapshot snapshot;
        try {
            snapshot = editor.load();
        } catch (ProfileValidationException e) {
            // Not even YAML: there is no document to put in the forms, and saving one would overwrite the file.
            ObjectNode out = json.createObjectNode();
            out.put("error", "profile.yaml is not valid YAML; fix it in a text editor, then reload");
            out.set("violations", violations(e.violations()));
            sendJson(exchange, 422, out);
            return;
        }
        ObjectNode body = json.createObjectNode();
        body.set("document", json.readTree(snapshot.json()));
        body.put("version", snapshot.version());
        body.set("findings", findings(snapshot.findings()));
        body.set("invalid", violations(snapshot.invalid()));
        exchange.getResponseHeaders().set("ETag", "\"" + snapshot.version() + "\"");
        sendJson(exchange, 200, body);
    }

    private void putProfile(HttpExchange exchange) throws IOException {
        String expected = unquote(exchange.getRequestHeaders().getFirst("If-Match"));
        if (expected == null) {
            error(exchange, 428, "If-Match with the version you loaded is required");
            return;
        }
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readNBytes((int) StudioGuard.MAX_BODY_BYTES + 1);
            if (bytes.length > StudioGuard.MAX_BODY_BYTES) {
                error(exchange, 413, "body larger than 1 MiB");
                return;
            }
            body = new String(bytes, StandardCharsets.UTF_8);
        }
        switch (editor.save(body, expected)) {
            case Saved s -> {
                ObjectNode out = json.createObjectNode();
                out.put("version", s.version());
                out.set("findings", findings(s.findings()));
                exchange.getResponseHeaders().set("ETag", "\"" + s.version() + "\"");
                sendJson(exchange, 200, out);
            }
            case Invalid i -> {
                ObjectNode out = json.createObjectNode();
                out.put("error", "the profile is not valid; nothing was saved");
                out.set("violations", violations(i.violations()));
                sendJson(exchange, 422, out);
            }
            case Conflict c -> {
                ObjectNode out = json.createObjectNode();
                out.put("error", "the file changed on disk; reload before saving");
                out.put("version", c.currentVersion());
                sendJson(exchange, 409, out);
            }
        }
    }

    // ------------------------------------------------------------------ static files and preview

    private void asset(HttpExchange exchange, String name) throws IOException {
        String type = ASSETS.get(name); // allow-list: no path from the request reaches the classpath lookup
        if (type == null) {
            error(exchange, 404, "not found");
            return;
        }
        try (InputStream in = StudioServer.class.getResourceAsStream(UI + name)) {
            if (in == null) {
                error(exchange, 404, "not found");
                return;
            }
            send(exchange, 200, type, in.readAllBytes(), UI_CSP);
        }
    }

    private void preview(HttpExchange exchange, PortfolioRenderer renderer, String file, String index)
            throws IOException {
        String wanted = file.isEmpty() ? index : file;
        Profile profile;
        try {
            profile = saved.get();
        } catch (ProfileValidationException e) {
            error(exchange, 409, "the saved profile is not valid yet");
            return;
        }
        Optional<Artifact> artifact = renderer.render(profile).stream()
                .filter(a -> a.relativePath().equals(wanted))
                .findFirst();
        if (artifact.isEmpty()) {
            error(exchange, 404, "not found");
            return;
        }
        send(exchange, 200, contentType(wanted), artifact.get().bytes(), PREVIEW_CSP);
    }

    static String contentType(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (lower.endsWith(".md")) return "text/markdown; charset=utf-8";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------ JSON helpers

    private ArrayNode findings(List<Finding> findings) {
        ArrayNode array = json.createArrayNode();
        for (Finding f : findings) {
            ObjectNode node = array.addObject();
            node.put("severity", f.severity().name().toLowerCase(java.util.Locale.ROOT));
            node.put("path", f.path());
            node.put("message", f.message());
            node.put("hint", f.hint());
        }
        return array;
    }

    private ArrayNode violations(List<Violation> violations) {
        ArrayNode array = json.createArrayNode();
        for (Violation v : violations) {
            array.addObject().put("path", v.path()).put("message", v.message());
        }
        return array;
    }

    private static String unquote(String etag) {
        if (etag == null || etag.isBlank()) {
            return null;
        }
        String value = etag.strip();
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    private static long contentLength(String header) {
        try {
            return header == null ? 0 : Long.parseLong(header.strip());
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE; // a length we cannot read is refused as too large
        }
    }

    // ------------------------------------------------------------------ responses

    private void sendJson(HttpExchange exchange, int status, JsonNode body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8",
                json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8), UI_CSP);
    }

    private void error(HttpExchange exchange, int status, String message) throws IOException {
        ObjectNode body = json.createObjectNode();
        body.put("error", message);
        sendJson(exchange, status, body);
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body, String csp)
            throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", type);
        headers.set("Content-Security-Policy", csp);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Cache-Control", "no-store");
        // The preview is shown in a sandboxed iframe, which has an opaque origin: its CSS and JS must be loadable
        // cross-origin. It only holds the public portfolio; the API and the editor stay same-origin.
        headers.set("Cross-Origin-Resource-Policy", csp.equals(PREVIEW_CSP) ? "cross-origin" : "same-origin");
        boolean head = exchange.getRequestMethod().equals("HEAD");
        exchange.sendResponseHeaders(status, head ? -1 : body.length);
        if (!head) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
