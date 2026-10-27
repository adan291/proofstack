package dev.proofstack.adapter.out.llm;

import tools.jackson.databind.ObjectMapper;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;
import dev.proofstack.core.port.out.ContentGenerator;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Factory used by the composition root. Configuration comes from the environment so secrets
 * never appear in command lines or shell history.
 */
public final class LlmProviders {

    public static final String GEMINI_API_KEY = "GEMINI_API_KEY";
    public static final String GEMINI_BASE_URL = "PROOFSTACK_GEMINI_BASE_URL";
    public static final String OLLAMA_HOST = "OLLAMA_HOST";
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    public enum Provider {
        OLLAMA, GEMINI;

        public static Provider parse(String value) {
            try {
                return valueOf(value.strip().toUpperCase(Locale.ROOT));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("unknown provider '" + value + "'. Use one of: "
                        + Arrays.toString(values()).toLowerCase(Locale.ROOT));
            }
        }
    }

    private LlmProviders() {
    }

    /**
     * @param model {@code null} selects the provider default
     * @param env   environment variables (inject {@code System.getenv()} in production)
     */
    public static ContentGenerator create(Provider provider, String model, Map<String, String> env) {
        return create(provider, model, env, REQUEST_TIMEOUT);
    }

    static ContentGenerator create(Provider provider, String model, Map<String, String> env, Duration timeout) {
        ObjectMapper json = new ObjectMapper();
        HttpJson http = new HttpJson(HttpJson.defaultClient(), json, timeout);
        LlmClient client = switch (provider) {
            case GEMINI -> new GeminiClient(http,
                    uri(env.get(GEMINI_BASE_URL), GeminiClient.DEFAULT_BASE_URL),
                    env.get(GEMINI_API_KEY),
                    blankToDefault(model, GeminiClient.DEFAULT_MODEL));
            case OLLAMA -> new OllamaClient(http,
                    uri(env.get(OLLAMA_HOST), OllamaClient.DEFAULT_HOST),
                    blankToDefault(model, OllamaClient.DEFAULT_MODEL));
        };
        return new LlmContentGenerator(client, new PromptLibrary(), json);
    }

    private static URI uri(String value, URI fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String v = value.strip();
        // OLLAMA_HOST is often set as "127.0.0.1:11434" without scheme.
        URI uri = URI.create(v.contains("://") ? v : "http://" + v);
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
            throw new ContentGenerationException("unsupported URL scheme in " + v, Kind.PROVIDER_UNAVAILABLE);
        }
        return uri;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
