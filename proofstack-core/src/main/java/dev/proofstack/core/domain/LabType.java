package dev.proofstack.core.domain;

import java.util.Optional;

/** Kind of lab. Each lab type may map onto an area of the architecture map. */
public enum LabType {
    SOFTWARE(null),
    AI(Area.AI),
    HARDWARE(Area.HARDWARE),
    AUTOMATION(Area.HARDWARE);

    private final Area area;

    LabType(Area area) {
        this.area = area;
    }

    public Optional<Area> area() {
        return Optional.ofNullable(area);
    }
}
