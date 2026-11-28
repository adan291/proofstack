package dev.proofstack.adapter.out.llm;

import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;

/**
 * Turns a non-2xx answer into one message the author can act on. The body of a 401 or 403 is never shown: some
 * providers echo part of the key in it.
 */
final class ProviderErrors {

    private ProviderErrors() {
    }

    static ContentGenerationException of(HttpJson.HttpStatusException e, String provider, String model) {
        String message = switch (e.status()) {
            case 401, 403 -> provider + " answered HTTP " + e.status() + " (the API key was rejected or has no access to the model)";
            case 404 -> e.getMessage() + " (check the URL and the model name '" + model + "')";
            case 429 -> provider + " answered HTTP 429 (rate limit or quota exceeded, try again later)";
            default -> e.status() >= 500
                    ? provider + " answered HTTP " + e.status() + " (the provider failed, try again later)"
                    : e.getMessage();
        };
        return new ContentGenerationException(message, Kind.PROVIDER_UNAVAILABLE, null);
    }
}
