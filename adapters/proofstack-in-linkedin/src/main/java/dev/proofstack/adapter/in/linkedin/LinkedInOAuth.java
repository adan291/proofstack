package dev.proofstack.adapter.in.linkedin;

import com.sun.net.httpserver.HttpServer;
import dev.proofstack.core.port.out.ProjectSource.ImportException;
import dev.proofstack.core.port.out.ProjectSource.ImportException.Kind;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * LinkedIn 3-legged OAuth (authorization code) for a local CLI.
 *
 * <ul>
 *   <li>The callback server listens only on the loopback interface, on the port of the registered redirect URI,
 *       and only for the duration of the login.</li>
 *   <li>{@code state} is 256 random bits, compared in constant time: protects against CSRF / code injection.</li>
 *   <li>The callback page is static text: nothing from the request is reflected (no XSS).</li>
 *   <li>The access token lives only in memory; it is never written to disk or printed.</li>
 * </ul>
 */
final class LinkedInOAuth {

    static final String SCOPE = "r_dma_portability_self_serve";
    static final URI DEFAULT_AUTH = URI.create("https://www.linkedin.com");

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final URI authBase;
    private final String clientId;
    private final String clientSecret;
    private final URI redirectUri;
    private final Duration timeout;

    LinkedInOAuth(HttpClient http, URI authBase, String clientId, String clientSecret, URI redirectUri,
                  Duration timeout) {
        this.http = http;
        this.authBase = authBase;
        this.clientId = require(clientId, LinkedInMemberDataSource.CLIENT_ID_ENV);
        this.clientSecret = require(clientSecret, LinkedInMemberDataSource.CLIENT_SECRET_ENV);
        this.redirectUri = loopback(redirectUri);
        this.timeout = timeout;
    }

    /** Opens the consent page through {@code openBrowser}, waits for the callback and returns the access token. */
    String login(Consumer<URI> openBrowser) {
        String state = randomState();
        CompletableFuture<Map<String, String>> callback = new CompletableFuture<>();
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", redirectUri.getPort()), 0);
        } catch (IOException e) {
            throw new ImportException("cannot listen on " + redirectUri + " (is the port in use?)", Kind.UNAVAILABLE, e);
        }
        String path = redirectUri.getPath() == null || redirectUri.getPath().isEmpty() ? "/" : redirectUri.getPath();
        server.createContext(path, exchange -> {
            Map<String, String> params = query(exchange.getRequestURI().getRawQuery());
            byte[] page = "Proofstack: you can close this tab and go back to the terminal."
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().add("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, page.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(page);
            }
            callback.complete(params);
        });
        server.start();
        try {
            openBrowser.accept(authorizationUri(state));
            Map<String, String> params = callback.get(timeout.toSeconds(), TimeUnit.SECONDS);
            if (params.containsKey("error")) {
                throw new ImportException("LinkedIn authorisation was not granted (" + params.get("error") + ")",
                        Kind.INVALID_INPUT);
            }
            if (!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),
                    params.getOrDefault("state", "").getBytes(StandardCharsets.UTF_8))) {
                throw new ImportException("OAuth state mismatch: the callback did not come from this login",
                        Kind.INVALID_INPUT);
            }
            String code = params.get("code");
            if (code == null || code.isBlank()) {
                throw new ImportException("LinkedIn did not return an authorisation code", Kind.UNAVAILABLE);
            }
            return exchange(code);
        } catch (TimeoutException e) {
            throw new ImportException("no LinkedIn login within " + timeout.toSeconds() + "s", Kind.UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImportException("login interrupted", Kind.UNAVAILABLE, e);
        } catch (ExecutionException e) {
            throw new ImportException("login failed", Kind.UNAVAILABLE, e);
        } finally {
            server.stop(0);
        }
    }

    URI authorizationUri(String state) {
        return authBase.resolve("/oauth/v2/authorization?response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri.toString())
                + "&state=" + enc(state)
                + "&scope=" + enc(SCOPE));
    }

    private String exchange(String code) {
        String form = "grant_type=authorization_code&code=" + enc(code) + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret) + "&redirect_uri=" + enc(redirectUri.toString());
        HttpRequest request = HttpRequest.newBuilder(authBase.resolve("/oauth/v2/accessToken"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ImportException("cannot reach LinkedIn to finish the login", Kind.UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImportException("login interrupted", Kind.UNAVAILABLE, e);
        }
        JsonNode body;
        try {
            body = json.readTree(response.body());
        } catch (JacksonException e) {
            throw new ImportException("LinkedIn answered the token request with HTTP " + response.statusCode(),
                    Kind.UNAVAILABLE, e);
        }
        String token = body.path("access_token").asString("");
        if (response.statusCode() != 200 || token.isEmpty()) {
            // Only LinkedIn's error code is shown: never the request (it contains the client secret).
            throw new ImportException("LinkedIn refused the token request: "
                    + body.path("error").asString("HTTP " + response.statusCode()), Kind.UNAVAILABLE);
        }
        return token;
    }

    static URI loopback(URI uri) {
        Objects.requireNonNull(uri, LinkedInMemberDataSource.REDIRECT_ENV);
        String host = uri.getHost() == null ? "" : uri.getHost();
        if (!"http".equals(uri.getScheme()) || !(host.equals("127.0.0.1") || host.equals("localhost"))
                || uri.getPort() < 1) {
            throw new ImportException(LinkedInMemberDataSource.REDIRECT_ENV
                    + " must be a loopback URL with port, e.g. http://127.0.0.1:63141/callback", Kind.INVALID_INPUT);
        }
        return uri;
    }

    private static String randomState() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static Map<String, String> query(String raw) {
        Map<String, String> params = new HashMap<>();
        if (raw == null) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.putIfAbsent(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String require(String value, String env) {
        if (value == null || value.isBlank()) {
            throw new ImportException(env + " is not set (see docs/linkedin-api.md)", Kind.INVALID_INPUT);
        }
        return value.strip();
    }
}
