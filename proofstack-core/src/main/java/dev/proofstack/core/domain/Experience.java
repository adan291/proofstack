package dev.proofstack.core.domain;

import java.util.List;

/** A job or engagement. */
public record Experience(
        String id,
        String role,
        String company,
        String location,
        Period period,
        List<String> highlights,
        List<String> technologies,
        List<String> evidenceIds) {

    public Experience {
        id = Checks.requireText(id, "id");
        role = Checks.requireText(role, "role");
        company = Checks.requireText(company, "company");
        location = Checks.optionalText(location);
        Checks.requireNonNull(period, "period");
        highlights = Checks.texts(highlights, "highlights");
        technologies = Checks.texts(technologies, "technologies");
        evidenceIds = Checks.texts(evidenceIds, "evidence");
    }

    public boolean current() {
        return period.isOngoing();
    }
}
