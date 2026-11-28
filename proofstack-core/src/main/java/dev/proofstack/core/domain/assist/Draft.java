package dev.proofstack.core.domain.assist;

import java.util.List;

/** What the model answered, before the core checks it. */
public record Draft(String text, List<String> changes, List<String> questions) {

    public Draft {
        text = text == null ? "" : text;
        changes = changes == null ? List.of() : List.copyOf(changes);
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
