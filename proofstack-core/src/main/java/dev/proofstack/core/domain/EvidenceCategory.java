package dev.proofstack.core.domain;

import java.util.List;

/** Kind of competence an {@link Evidence} proves. */
public enum EvidenceCategory {
    LEADERSHIP(List.of(Area.LEADERSHIP)),
    MENTORING(List.of(Area.LEADERSHIP)),
    COMMUNICATION(List.of(Area.COMMUNICATION)),
    DOCUMENTATION(List.of(Area.COMMUNICATION)),
    TECHNICAL(List.of());

    private final List<Area> defaultAreas;

    EvidenceCategory(List<Area> defaultAreas) {
        this.defaultAreas = defaultAreas;
    }

    /** Areas used when the evidence does not declare any explicitly. */
    public List<Area> defaultAreas() {
        return defaultAreas;
    }
}
