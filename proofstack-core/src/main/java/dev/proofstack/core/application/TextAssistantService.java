package dev.proofstack.core.application;

import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.assist.AssistMode;
import dev.proofstack.core.domain.assist.AssistantBrief;
import dev.proofstack.core.domain.assist.Draft;
import dev.proofstack.core.domain.assist.FieldPath;
import dev.proofstack.core.domain.assist.Suggestion;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;
import dev.proofstack.core.domain.content.GroundingCheck;
import dev.proofstack.core.port.in.TextAssistant;
import dev.proofstack.core.port.out.ProfileJsonCodec;
import dev.proofstack.core.port.out.TextDrafter;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds the brief for one field and checks what comes back (ADR-006, ADR-017). The context is the profile without
 * phone and email; the proposal is trimmed to the field's limit and its numbers are checked against the profile.
 */
public final class TextAssistantService implements TextAssistant {

    public static final int MAX_CURRENT_TEXT = 4000;
    public static final int MAX_REQUEST = 2000;
    static final int MAX_CHANGES = 5;
    static final int MAX_QUESTIONS = 3;

    private final TextDrafter drafter;
    private final ProfileJsonCodec codec;

    public TextAssistantService(TextDrafter drafter, ProfileJsonCodec codec) {
        this.drafter = Objects.requireNonNull(drafter, "drafter");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public Suggestion suggest(Profile profile, FieldPath path, String currentText, String request, AssistMode mode,
                              Locale targetLanguage) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(mode, "mode");
        String current = currentText == null ? "" : currentText;
        String ask = request == null ? "" : request;
        validate(current, ask, mode);

        String context = codec.toJson(withoutPrivateData(profile));
        String language = mode == AssistMode.TRANSLATE ? languageTag(targetLanguage) : profile.language();
        AssistantBrief brief = new AssistantBrief(path.value(), path.purposeKey(), path.area().orElse(""), mode,
                mode == AssistMode.JOB_OFFER ? "" : ask, current, mode == AssistMode.JOB_OFFER ? ask : "",
                language, language, path.maxLength(), context);

        Draft draft = drafter.draft(brief);
        String text = draft.text().strip();
        if (text.isEmpty()) {
            throw new ContentGenerationException("the model proposed an empty text", Kind.INVALID_OUTPUT);
        }
        if (text.length() > path.maxLength()) {
            text = text.substring(0, path.maxLength()).stripTrailing();
        }
        List<String> unsupported = GroundingCheck.findUnsupportedNumbers(text, context + "\n" + current);
        return new Suggestion(text, first(draft.changes(), MAX_CHANGES), first(draft.questions(), MAX_QUESTIONS),
                unsupported);
    }

    private static void validate(String current, String request, AssistMode mode) {
        if (current.length() > MAX_CURRENT_TEXT) {
            throw new IllegalArgumentException("the text is longer than " + MAX_CURRENT_TEXT + " characters");
        }
        if (request.length() > MAX_REQUEST) {
            throw new IllegalArgumentException("the request is longer than " + MAX_REQUEST + " characters");
        }
        if (mode == AssistMode.TRANSLATE && current.isBlank()) {
            throw new IllegalArgumentException("there is no text to translate");
        }
        if (mode == AssistMode.JOB_OFFER && request.isBlank()) {
            throw new IllegalArgumentException("paste the job offer first");
        }
    }

    /** Phone and email are the only data kept out of the context: the rest is what the author wants to be read. */
    private static Profile withoutPrivateData(Profile p) {
        PersonalInfo person = p.person();
        PersonalInfo clean = new PersonalInfo(person.name(), person.headline(), person.summary(), person.location(),
                null, person.links(), null, person.contactMessage());
        return new Profile(p.language(), clean, p.experience(), p.education(), p.projects(), p.skills(), p.evidence(),
                p.labs(), p.languages(), p.certifications());
    }

    private static String languageTag(Locale locale) {
        if (locale == null || locale.getLanguage().isEmpty()) {
            throw new IllegalArgumentException("choose the language to translate to");
        }
        return locale.getLanguage().toLowerCase(Locale.ROOT);
    }

    private static List<String> first(List<String> items, int max) {
        return items.stream().map(String::strip).filter(s -> !s.isEmpty()).limit(max).toList();
    }
}
