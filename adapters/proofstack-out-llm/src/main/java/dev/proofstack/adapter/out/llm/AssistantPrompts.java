package dev.proofstack.adapter.out.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.proofstack.core.domain.assist.AssistMode;
import dev.proofstack.core.domain.assist.AssistantBrief;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The assistant's prompts: versioned resources under {@code /prompts/assistant/}, reviewed like code.
 * Everything that comes from the author or the profile is fenced in its own tag, and any tag of the prompt found
 * inside it is defanged, so data cannot close its block and pose as instructions (ADR-017).
 */
final class AssistantPrompts {

    private static final String DIR = "/prompts/assistant/";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    /** Every tag the task prompt uses. An opening or closing one inside the data loses its shape. */
    private static final Pattern PROMPT_TAG = Pattern.compile(
            "(?i)<(/?)(profile-data|current-text|request|job-offer|task|field|purpose)(?=[\\s>/])");

    private final String system = load("system.md");
    private final String task = load("task.md");
    private final JsonNode purposes;
    private final JsonNode tasks;
    private final JsonNode schema;

    AssistantPrompts() {
        ObjectMapper json = new ObjectMapper();
        try {
            purposes = json.readTree(load("purposes.json"));
            tasks = json.readTree(load("tasks.json"));
            schema = json.readTree(load("answer.schema.json"));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid assistant prompt resources", e);
        }
    }

    String system(AssistantBrief brief) {
        return fill(system, Map.of("language", PromptLibrary.languageName(brief.language()),
                "maxLength", String.valueOf(brief.maxLength())));
    }

    String user(AssistantBrief brief) {
        boolean offer = brief.mode() == AssistMode.JOB_OFFER;
        String taskText = fill(tasks.path(offer ? "job-offer" : brief.mode().name().toLowerCase(java.util.Locale.ROOT))
                .asString(), Map.of("targetLanguage", PromptLibrary.languageName(brief.targetLanguage())));
        return fill(task, Map.of(
                "path", defang(brief.path()),
                "purpose", purpose(brief.purposeKey(), brief.area()),
                "task", taskText,
                "request", defang(brief.request()),
                "currentText", defang(brief.currentText()),
                "jobOffer", defang(brief.jobOffer()),
                "profileJson", defang(brief.profileJson()))).strip();
    }

    String purpose(String purposeKey, String area) {
        String text = purposes.path(purposeKey).asString("");
        if (text.isEmpty()) {
            throw new IllegalStateException("No purpose text for " + purposeKey);
        }
        return text.replace("{area}", area);
    }

    /** The schema providers get: meta keywords such as {@code $schema} are for local validation only. */
    JsonNode providerSchema() {
        tools.jackson.databind.node.ObjectNode copy = ((tools.jackson.databind.node.ObjectNode) schema).deepCopy();
        copy.remove("$schema");
        return copy;
    }

    JsonNode localSchema() {
        return schema;
    }

    /** One pass, so a value that contains {@code {{name}}} is never expanded again. */
    private static String fill(String template, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? m.group() : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    static String defang(String text) {
        return PROMPT_TAG.matcher(text == null ? "" : text).replaceAll("<\\\\$1$2");
    }

    private static String load(String name) {
        try (InputStream in = AssistantPrompts.class.getResourceAsStream(DIR + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing prompt resource " + DIR + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
