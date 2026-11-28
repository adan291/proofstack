package dev.proofstack.core.domain.assist;

import java.util.List;

/**
 * A proposal for one field. Nothing is applied: the author decides. {@code unsupportedNumbers} are numbers in
 * {@code text} that appear neither in the profile nor in the text being edited (ADR-006).
 */
public record Suggestion(String text, List<String> changes, List<String> questions, List<String> unsupportedNumbers) {

    public Suggestion {
        changes = List.copyOf(changes);
        questions = List.copyOf(questions);
        unsupportedNumbers = List.copyOf(unsupportedNumbers);
    }
}
