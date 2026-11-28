package dev.proofstack.adapter.out.llm;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Shared JSON-over-HTTP plumbing with consistent, secret-free error messages. */
final class HttpJson {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_ERROR_BODY = 300;

    private final HttpClient http;
    private final ObjectMapper json;
    private final Duration requestTimeout;

    HttpJson(HttpClient http, ObjectMapper json, Duration requestTimeout) {
        this.http = http;
        this.json = json;
        this.requestTimeout = requestTimeout;
    }

    static HttpClient defaultClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    ObjectMapper mapper() {
        return json;
    }

    /**
     * POSTs {@code body} and returns the parsed response.
     *
     * @param secret value that must never leak into error messages (may be null)
     */
    JsonNode post(URI uri, Map<String, String> headers, JsonNode body, String provider, String secret) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(write(body), StandardCharsets.UTF_8));
        headers.forEach(request::header);

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw unavailable(provider + " did not answer within " + requestTimeout.toSeconds() + "s", e);
        } catch (ConnectException e) {
            throw unavailable("cannot connect to " + provider + " at " + origin(uri), e);
        } catch (IOException e) {
            throw unavailable(provider + " request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(provider + " request interrupted", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new HttpStatusException(response.statusCode(),
                    provider + " answered HTTP " + response.statusCode() + ": " + redact(response.body(), secret));
        }
        try {
            return json.readTree(response.body());
        } catch (JacksonException e) {
            throw new ContentGenerationException(provider + " returned a non-JSON response", Kind.INVALID_OUTPUT, e);
        }
    }

    /** {@code base} plus {@code path}, keeping the base's query ({@code ?api-version=...} on Azure). */
    static URI endpoint(URI base, String path) {
        String basePath = base.getRawPath() == null ? "" : base.getRawPath();
        while (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        String query = base.getRawQuery() == null ? "" : "?" + base.getRawQuery();
        return URI.create(base.getScheme() + "://" + base.getRawAuthority() + basePath + "/" + path + query);
    }

    private String write(JsonNode body) {
        try {
            return json.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    static String redact(String text, String secret) {
        String safe = text == null ? "" : text;
        if (secret != null && !secret.isEmpty()) {
            safe = safe.replace(secret, "***");
        }
        safe = safe.replaceAll("(?i)(bearer\\s+)\\S+", "$1***").replaceAll("\\b(sk|key|AIza)[-_A-Za-z0-9]{12,}", "***");
        safe = safe.replaceAll("\\s+", " ").strip();
        return safe.length() > MAX_ERROR_BODY ? safe.substring(0, MAX_ERROR_BODY) + "…" : safe;
    }

    private static String origin(URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    private static ContentGenerationException unavailable(String message, Throwable cause) {
        return new ContentGenerationException(message, Kind.PROVIDER_UNAVAILABLE, cause);
    }

    /** Non-2xx answer; clients turn it into a provider-specific, actionable message. */
    static final class HttpStatusException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
        private final int status;

        HttpStatusException(int status, String message) {
            super(message);
            this.status = status;
        }

        int status() {
            return status;
        }
    }
}
