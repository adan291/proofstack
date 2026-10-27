package dev.proofstack.core.domain;

import java.util.List;

/** Small guard helpers shared by the domain records. */
final class Checks {

    private Checks() {
    }

    /** Requires a non-blank text and returns it trimmed. */
    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException(field, "must not be blank");
        }
        return value.strip();
    }

    /** Returns the trimmed text, or {@code null} when absent/blank. */
    static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new InvalidValueException(field, "is required");
        }
        return value;
    }

    /** Null-safe immutable copy that also rejects null elements. */
    static <T> List<T> list(List<T> values, String field) {
        if (values == null) {
            return List.of();
        }
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == null) {
                throw new InvalidValueException(field + "[" + i + "]", "must not be null");
            }
        }
        return List.copyOf(values);
    }

    /** Immutable copy of non-blank, trimmed strings. */
    static List<String> texts(List<String> values, String field) {
        List<String> copy = list(values, field);
        for (int i = 0; i < copy.size(); i++) {
            requireText(copy.get(i), field + "[" + i + "]");
        }
        return copy.stream().map(String::strip).toList();
    }
}
