package dev.proofstack.adapter.out.llm;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * The provider the assistant talks to, with the key already resolved. {@link #toString()} never prints the key,
 * so a settings object in a log line or an exception message cannot leak it.
 *
 * @param apiKey    {@code null} for local models that need none
 * @param keySource where the key came from, for the settings screen: {@code none}, {@code env} or {@code file}
 * @param authHeader header that carries the key; {@code Authorization} sends {@code Bearer <key>}, any other sends the key as is
 * @param headers   extra headers (OpenRouter's {@code HTTP-Referer}, for instance), never secret
 * @param insecureHttpConfirmed the author confirmed, in the settings screen, that plain http to a non-local host is intended
 */
public record AiSettings(
        Protocol protocol,
        URI baseUrl,
        String model,
        String apiKey,
        String keySource,
        String authHeader,
        Map<String, String> headers,
        int timeoutSeconds,
        double temperature,
        boolean insecureHttpConfirmed) {

    public static final int DEFAULT_TIMEOUT_SECONDS = 60;
    public static final double DEFAULT_TEMPERATURE = 0.4;
    private static final Set<String> LOCAL_HOSTS = Set.of("127.0.0.1", "localhost", "::1", "[::1]", "0:0:0:0:0:0:0:1");

    public AiSettings {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.strip();
        keySource = apiKey == null ? "none" : keySource;
        authHeader = authHeader == null || authHeader.isBlank() ? protocol.defaultAuthHeader() : authHeader.strip();
    }

    /** True when requests stay on this machine, so nothing needs the author's consent. */
    public boolean local() {
        String host = baseUrl.getHost();
        return host != null && LOCAL_HOSTS.contains(host.toLowerCase(java.util.Locale.ROOT));
    }

    /** Host shown in the consent question and stored once accepted. */
    public String providerHost() {
        return baseUrl.getHost() == null ? baseUrl.toString() : baseUrl.getHost().toLowerCase(java.util.Locale.ROOT);
    }

    /** Headers for the request: the extra ones and, when there is a key, the authentication one. */
    Map<String, String> requestHeaders() {
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>(headers);
        if (apiKey != null) {
            out.put(authHeader, authHeader.equalsIgnoreCase("Authorization") ? "Bearer " + apiKey : apiKey);
        }
        return out;
    }

    @Override
    public String toString() {
        return "AiSettings[" + protocol.id() + " " + baseUrl + " model=" + model
                + " key=" + (apiKey == null ? "none" : "***") + "]";
    }
}
