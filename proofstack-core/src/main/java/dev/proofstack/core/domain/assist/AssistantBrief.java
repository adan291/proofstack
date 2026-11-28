package dev.proofstack.core.domain.assist;

import java.util.Objects;

/**
 * Everything the model needs for one field, already decided by the core: which field it is, what it is for, which
 * language to write in and which facts it may use. {@code profileJson} has no phone and no email.
 * {@code area} is empty and {@code jobOffer} is empty unless the field or the mode uses them.
 */
public record AssistantBrief(
        String path,
        String purposeKey,
        String area,
        AssistMode mode,
        String request,
        String currentText,
        String jobOffer,
        String language,
        String targetLanguage,
        int maxLength,
        String profileJson) {

    public AssistantBrief {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(purposeKey, "purposeKey");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(profileJson, "profileJson");
        area = area == null ? "" : area;
        request = request == null ? "" : request;
        currentText = currentText == null ? "" : currentText;
        jobOffer = jobOffer == null ? "" : jobOffer;
        targetLanguage = targetLanguage == null ? language : targetLanguage;
    }
}
