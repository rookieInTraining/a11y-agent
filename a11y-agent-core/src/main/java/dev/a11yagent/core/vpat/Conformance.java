package dev.a11yagent.core.vpat;

/** ITI VPAT 2.5 conformance level terms. */
public enum Conformance {
    SUPPORTS("Supports"),
    PARTIALLY_SUPPORTS("Partially Supports"),
    DOES_NOT_SUPPORT("Does Not Support"),
    NOT_APPLICABLE("Not Applicable"),
    NOT_EVALUATED("Not Evaluated");

    private final String label;

    Conformance(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String cssClass() {
        return label.replace(" ", "");
    }
}
