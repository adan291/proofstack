package dev.proofstack.core.domain;

import java.util.List;

/**
 * Concrete proof of a competence, in context / action / result form.
 * When no areas are given, they are derived from the {@link EvidenceCategory}.
 */
public record Evidence(
        String id,
        EvidenceCategory category,
        String title,
        String context,
        String action,
        String result,
        Link link,
        List<Area> areas) {

    public Evidence {
        id = Checks.requireText(id, "id");
        Checks.requireNonNull(category, "category");
        title = Checks.requireText(title, "title");
        context = Checks.requireText(context, "context");
        action = Checks.requireText(action, "action");
        result = Checks.requireText(result, "result");
        areas = Checks.list(areas, "areas").stream().distinct().toList();
        if (areas.isEmpty()) {
            areas = category.defaultAreas();
        }
    }
}
