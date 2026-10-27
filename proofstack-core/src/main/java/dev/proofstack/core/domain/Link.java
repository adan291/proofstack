package dev.proofstack.core.domain;

import java.util.Locale;

/**
 * External link. Only {@code http}, {@code https} and {@code mailto} are accepted so that a
 * profile can never inject {@code javascript:} or other dangerous schemes into rendered output.
 */
public record Link(String label, String url) {

    public Link {
        label = Checks.requireText(label, "label");
        url = Checks.requireText(url, "url");
        String lower = url.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:"))) {
            throw new InvalidValueException("url", "must use http, https or mailto");
        }
    }
}
