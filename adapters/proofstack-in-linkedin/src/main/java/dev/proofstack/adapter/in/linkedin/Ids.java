package dev.proofstack.adapter.in.linkedin;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Generates schema-compliant, unique ids ({@code ^[a-z0-9][a-z0-9-]{0,63}$}) from free text. */
final class Ids {

    private final Set<String> used = new HashSet<>();

    String unique(String text, String fallback) {
        String base = slug(text);
        if (base.isEmpty()) {
            base = fallback;
        }
        String candidate = base;
        for (int n = 2; !used.add(candidate); n++) {
            String suffix = "-" + n;
            candidate = base.substring(0, Math.min(base.length(), 64 - suffix.length())) + suffix;
        }
        return candidate;
    }

    static String slug(String text) {
        if (text == null) {
            return "";
        }
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > 64) {
            slug = slug.substring(0, 64).replaceAll("-+$", "");
        }
        return slug;
    }
}
