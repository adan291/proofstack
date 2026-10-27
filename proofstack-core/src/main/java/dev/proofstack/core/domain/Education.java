package dev.proofstack.core.domain;

import java.util.List;

/** Formal education or long-form training. */
public record Education(
        String id,
        String title,
        String institution,
        Period period,
        List<String> highlights) {

    public Education {
        id = Checks.requireText(id, "id");
        title = Checks.requireText(title, "title");
        institution = Checks.requireText(institution, "institution");
        Checks.requireNonNull(period, "period");
        highlights = Checks.texts(highlights, "highlights");
    }
}
