package dev.proofstack.core.domain;

/** A named skill placed in one area of the map. Deliberately no "level" stars: evidence proves level. */
public record Skill(String name, Area area) {

    public Skill {
        name = Checks.requireText(name, "name");
        Checks.requireNonNull(area, "area");
    }
}
