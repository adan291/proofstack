package dev.proofstack.core.domain;

import java.util.List;

/** A group of experiments (AI Lab, Hardware Lab...) referencing projects. */
public record Lab(
        String id,
        String name,
        LabType type,
        String description,
        List<String> projectIds) {

    public Lab {
        id = Checks.requireText(id, "id");
        name = Checks.requireText(name, "name");
        Checks.requireNonNull(type, "type");
        description = Checks.requireText(description, "description");
        projectIds = Checks.texts(projectIds, "projects");
    }
}
