package dev.proofstack.core.domain.knowledge;

import java.util.List;
import java.util.Objects;

/**
 * Final answer. {@code UNKNOWN} has no text: the UI shows a localized "not in the portfolio" message,
 * so the system never fills the gap with plausible-sounding invention.
 */
public record Answer(Status status, String text, List<KnowledgeChunk> citations, List<String> warnings) {

    public enum Status { ANSWERED, UNKNOWN }

    public Answer {
        Objects.requireNonNull(status, "status");
        text = text == null ? "" : text;
        citations = citations == null ? List.of() : List.copyOf(citations);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (status == Status.ANSWERED && (text.isBlank() || citations.isEmpty())) {
            throw new IllegalArgumentException("an answered question needs text and at least one citation");
        }
    }

    public static Answer unknown(List<String> warnings) {
        return new Answer(Status.UNKNOWN, "", List.of(), warnings);
    }
}
