package dev.proofstack.adapter.in.studio;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import dev.proofstack.core.application.TextAssistantService;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.domain.assist.AssistMode;
import dev.proofstack.core.domain.assist.FieldPath;
import dev.proofstack.core.domain.assist.Suggestion;
import dev.proofstack.core.domain.assist.UnsupportedFieldException;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.port.in.EditProfileUseCase;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The {@code /api/ai/*} routes as plain functions from a JSON request to a status and a JSON answer, so they can be
 * tested without a socket. Every route sits behind {@link StudioGuard}. The context for the model is the profile saved
 * on disk; only {@code currentText} may be a draft. Nothing here writes the profile being edited.
 */
final class AssistRoutes {

    record Reply(int status, ObjectNode body) {
    }

    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2}");

    private final StudioAssistant assistant;
    private final Supplier<Profile> saved;
    private final StudioTranslation translation;
    private final JsonMapper json;
    /** One call to the provider at a time: it is slow, it may cost money and a double click must not double it. */
    private final AtomicBoolean busy = new AtomicBoolean();

    AssistRoutes(StudioAssistant assistant, Supplier<Profile> saved, StudioTranslation translation, JsonMapper json) {
        this.assistant = assistant;
        this.saved = saved;
        this.translation = translation;
        this.json = json;
    }

    boolean available() {
        return assistant != null;
    }

    Reply status() {
        if (assistant == null) {
            ObjectNode off = json.createObjectNode();
            off.put("configured", false).put("protocol", "").put("model", "").put("local", false).put("keySource", "none");
            return new Reply(200, off);
        }
        return new Reply(200, assistant.status());
    }

    Reply config() {
        ObjectNode out = assistant.config();
        ObjectNode other = out.putObject("translation");
        other.put("file", translation == null ? "" : translation.fileName());
        boolean exists = translation != null && translation.exists().getAsBoolean();
        other.put("exists", exists);
        other.put("language", exists ? otherLanguage() : "");
        return new Reply(200, out);
    }

    /** The language the other file is written in: what a translation must be translated into. */
    private String otherLanguage() {
        try {
            String language = json.readTree(translation.editor().load().json()).path("language").asString("en");
            return LANGUAGE.matcher(language.substring(0, Math.min(2, language.length()))).matches()
                    ? language.substring(0, 2) : "en";
        } catch (RuntimeException e) {
            return "en";
        }
    }

    Reply saveConfig(JsonNode update) {
        String warning;
        try {
            warning = assistant.saveConfig(update);
        } catch (IllegalArgumentException e) {
            return error(400, "invalid-config", e.getMessage());
        }
        ObjectNode out = config().body();
        if (warning != null) {
            out.put("warning", warning);
        }
        return new Reply(200, out);
    }

    Reply test() {
        if (!status().body().path("configured").asBoolean(false)) {
            return error(409, "not-configured", "no AI provider is configured");
        }
        if (!busy.compareAndSet(false, true)) {
            return error(429, "busy", "there is already a request to the AI in progress");
        }
        try {
            return new Reply(200, assistant.test());
        } finally {
            busy.set(false);
        }
    }

    Reply consent(JsonNode request) {
        String pending = assistant.pendingConsent();
        String host = request.path("host").asString("");
        if (pending == null || !pending.equalsIgnoreCase(host)) {
            return error(400, "bad-consent", "there is nothing to accept for that provider");
        }
        assistant.consent(pending);
        return new Reply(200, json.createObjectNode().put("accepted", pending));
    }

    Reply suggest(JsonNode request) {
        FieldPath path;
        AssistMode mode;
        Locale target;
        try {
            path = FieldPath.parse(request.path("path").asString(""));
            mode = mode(request.path("mode").asString("improve"));
            target = mode == AssistMode.TRANSLATE ? language(request.path("targetLanguage").asString("")) : Locale.ROOT;
            limit(request.path("currentText"), TextAssistantService.MAX_CURRENT_TEXT, "the text");
            limit(request.path("request"), TextAssistantService.MAX_REQUEST, "the request");
        } catch (UnsupportedFieldException e) {
            return error(400, "bad-field", e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(400, "bad-request", e.getMessage());
        }
        if (!status().body().path("configured").asBoolean(false)) {
            return error(409, "not-configured", "no AI provider is configured");
        }
        String pending = assistant.pendingConsent();
        if (pending != null) {
            ObjectNode body = json.createObjectNode();
            body.put("code", "consent-required").put("provider", pending)
                    .put("error", "your profile will be sent to " + pending + " to generate the proposal");
            return new Reply(409, body);
        }
        Profile profile;
        try {
            profile = saved.get();
        } catch (ProfileValidationException e) {
            return error(409, "saved-invalid", "the saved profile is not valid yet");
        }
        if (!busy.compareAndSet(false, true)) {
            return error(429, "busy", "there is already a request to the AI in progress");
        }
        try {
            Suggestion s = assistant.suggest(profile, path, request.path("currentText").asString(""),
                    request.path("request").asString(""), mode, target);
            ObjectNode out = json.createObjectNode();
            out.put("text", s.text());
            strings(out.putArray("changes"), s.changes());
            strings(out.putArray("questions"), s.questions());
            strings(out.putArray("unsupportedNumbers"), s.unsupportedNumbers());
            return new Reply(200, out);
        } catch (IllegalArgumentException e) {
            return error(400, "bad-request", e.getMessage());
        } catch (IllegalStateException e) {
            return error(409, "not-configured", e.getMessage());
        } catch (ContentGenerationException e) {
            return error(502, e.kind() == ContentGenerationException.Kind.INVALID_OUTPUT ? "invalid-output" : "provider",
                    e.getMessage());
        } finally {
            busy.set(false);
        }
    }

    /**
     * Writes an accepted translation into the same field of the other language file. Explicit like a save: the
     * author pressed the button that names the file.
     */
    Reply applyTranslation(JsonNode request) {
        if (translation == null || !translation.exists().getAsBoolean()) {
            return error(404, "no-other-file", "there is no file for the other language");
        }
        FieldPath path;
        try {
            path = FieldPath.parse(request.path("path").asString(""));
        } catch (UnsupportedFieldException e) {
            return error(400, "bad-field", e.getMessage());
        }
        String text = request.path("text").asString("").strip();
        if (text.isEmpty() || text.length() > path.maxLength()) {
            return error(400, "bad-request", "the text must have between 1 and " + path.maxLength() + " characters");
        }
        EditProfileUseCase.Snapshot snapshot;
        ObjectNode document;
        try {
            snapshot = translation.editor().load();
            JsonNode node = json.readTree(snapshot.json());
            if (!(node instanceof ObjectNode object)) {
                return error(409, "other-invalid", translation.fileName() + " is not valid yet");
            }
            document = object;
        } catch (ProfileValidationException | JacksonException e) {
            return error(409, "other-invalid", translation.fileName() + " is not valid yet");
        }
        try {
            JsonFieldEditor.set(document, path, text);
        } catch (IllegalArgumentException e) {
            return error(422, "missing-field", e.getMessage());
        }
        return switch (translation.editor().save(json.writeValueAsString(document), snapshot.version())) {
            case EditProfileUseCase.Saved s -> new Reply(200, json.createObjectNode()
                    .put("file", translation.fileName()).put("version", s.version()));
            case EditProfileUseCase.Invalid i -> {
                ObjectNode body = error(422, "invalid", translation.fileName() + " would not be valid; nothing was saved").body();
                body.set("violations", violations(i.violations()));
                yield new Reply(422, body);
            }
            case EditProfileUseCase.Conflict _ ->
                    error(409, "conflict", translation.fileName() + " changed on disk; try again");
        };
    }

    // ------------------------------------------------------------------ helpers

    private static AssistMode mode(String value) {
        return switch (value) {
            case "improve" -> AssistMode.IMPROVE;
            case "translate" -> AssistMode.TRANSLATE;
            case "job-offer" -> AssistMode.JOB_OFFER;
            default -> throw new IllegalArgumentException("mode must be improve, translate or job-offer");
        };
    }

    private static Locale language(String value) {
        if (!LANGUAGE.matcher(value).matches()) {
            throw new IllegalArgumentException("targetLanguage must be a two-letter code like en or es");
        }
        return Locale.forLanguageTag(value.toLowerCase(Locale.ROOT));
    }

    private static void limit(JsonNode node, int max, String what) {
        if (!node.isMissingNode() && !node.isNull() && !node.isString()) {
            throw new IllegalArgumentException(what + " must be text");
        }
        if (node.asString("").length() > max) {
            throw new IllegalArgumentException(what + " is longer than " + max + " characters");
        }
    }

    private static void strings(ArrayNode into, List<String> values) {
        values.forEach(into::add);
    }

    private ArrayNode violations(List<Violation> violations) {
        ArrayNode array = json.createArrayNode();
        violations.forEach(v -> array.addObject().put("path", v.path()).put("message", v.message()));
        return array;
    }

    private Reply error(int status, String code, String message) {
        return new Reply(status, json.createObjectNode().put("code", code).put("error", message));
    }
}
