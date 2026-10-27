package dev.proofstack.adapter.out.llm;

import dev.proofstack.core.domain.content.ContentFormat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Prompts are versioned resources ({@code /prompts/*.md}), reviewed like code instead of being
 * string literals scattered through the adapter.
 */
final class PromptLibrary {

    private static final String DATA_OPEN = "<profile-data>";
    private static final String DATA_CLOSE = "</profile-data>";

    private final String system;
    private final Map<ContentFormat, String> formats = new EnumMap<>(ContentFormat.class);

    PromptLibrary() {
        this.system = load("system.md");
        for (ContentFormat format : ContentFormat.values()) {
            formats.put(format, load(format.slug() + ".md"));
        }
    }

    String system(String language) {
        return system.replace("{{language}}", languageName(language));
    }

    /**
     * Builds the user prompt. Profile data is fenced and its closing tag escaped, so data
     * can never "close" the block and smuggle instructions (prompt injection mitigation).
     */
    String user(ContentFormat format, String language, String dataJson) {
        String safeData = dataJson.replace(DATA_CLOSE, "<\\/profile-data>");
        return formats.get(format).replace("{{language}}", languageName(language))
                + "\n\n" + DATA_OPEN + "\n" + safeData + "\n" + DATA_CLOSE + "\n";
    }

    static String languageName(String tag) {
        Locale locale = Locale.forLanguageTag(tag == null ? "en" : tag);
        String name = locale.getDisplayLanguage(Locale.ENGLISH);
        return name.isEmpty() ? "English" : name;
    }

    private static String load(String name) {
        try (InputStream in = PromptLibrary.class.getResourceAsStream("/prompts/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing prompt resource /prompts/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
