package dev.proofstack.adapter.in.studio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Who may talk to Studio. A local server is reachable from any web page the user opens, so every request is checked:
 *
 * <ul>
 *   <li><b>Host</b> must be the loopback name and port: defeats DNS rebinding.</li>
 *   <li><b>Token</b> (256 random bits, constant-time compare) on every API call: a page that does not know it gets
 *       nothing.</li>
 *   <li><b>Origin</b> must be Studio itself on writes, and the body must be JSON: no cross-site form can post.</li>
 * </ul>
 */
final class StudioGuard {

    static final String TOKEN_HEADER = "X-Studio-Token";
    static final long MAX_BODY_BYTES = 1024 * 1024;

    private final int port;
    private final byte[] token;
    private final String tokenText;

    StudioGuard(int port, String token) {
        this.port = port;
        this.tokenText = token;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String token() {
        return tokenText;
    }

    /** Empty when the request may go on; otherwise the status and reason to answer with. */
    Optional<Refusal> check(String method, String path, String host, String origin, String contentType,
                            String requestToken, long contentLength) {
        return check(method, path, host, origin, contentType, requestToken, contentLength, null);
    }

    /**
     * @param fetchSite the browser's {@code Sec-Fetch-Site}: the preview (the CV, the site) may only be opened by
     *                  Studio itself or by the user typing the URL, never by another page. Its static CSS and JS are
     *                  exempt: the sandboxed iframe has an opaque origin, so they always arrive as cross-site.
     */
    Optional<Refusal> check(String method, String path, String host, String origin, String contentType,
                            String requestToken, long contentLength, String fetchSite) {
        if (!allowedHost(host)) {
            return Optional.of(new Refusal(403, "host not allowed"));
        }
        if (path.startsWith("/preview/") && !staticAsset(path) && fetchSite != null
                && !fetchSite.equals("same-origin") && !fetchSite.equals("none")) {
            return Optional.of(new Refusal(403, "the preview can only be opened from Studio"));
        }
        if (contentLength > MAX_BODY_BYTES) {
            return Optional.of(new Refusal(413, "body larger than 1 MiB"));
        }
        if (path.startsWith("/api/") && !validToken(requestToken)) {
            return Optional.of(new Refusal(401, "missing or wrong " + TOKEN_HEADER));
        }
        boolean write = !method.equals("GET") && !method.equals("HEAD");
        if (write && !selfOrigin(origin)) {
            return Optional.of(new Refusal(403, "origin not allowed"));
        }
        if (write && (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json"))) {
            return Optional.of(new Refusal(415, "body must be application/json"));
        }
        return Optional.empty();
    }

    private static boolean staticAsset(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".css") || lower.endsWith(".js");
    }

    boolean allowedHost(String host) {
        return host != null && (host.equals("127.0.0.1:" + port) || host.equals("localhost:" + port));
    }

    boolean selfOrigin(String origin) {
        return origin != null && (origin.equals("http://127.0.0.1:" + port) || origin.equals("http://localhost:" + port));
    }

    boolean validToken(String candidate) {
        return candidate != null && MessageDigest.isEqual(token, candidate.getBytes(StandardCharsets.UTF_8));
    }

    record Refusal(int status, String reason) {
    }
}
