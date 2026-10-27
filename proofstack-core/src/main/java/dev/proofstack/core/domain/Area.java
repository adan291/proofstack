package dev.proofstack.core.domain;

/**
 * Areas shown as nodes in the architecture map. Declaration order is the display order.
 */
public enum Area {
    BACKEND("Backend"),
    ARCHITECTURE("Architecture"),
    MESSAGING("Messaging"),
    DATA("Data"),
    DEVOPS("DevOps & Observability"),
    AI("AI"),
    HARDWARE("Hardware & Automation"),
    LEADERSHIP("Leadership"),
    COMMUNICATION("Communication");

    private final String label;

    Area(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
