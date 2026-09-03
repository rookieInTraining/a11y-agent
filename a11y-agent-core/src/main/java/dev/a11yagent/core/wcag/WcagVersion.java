package dev.a11yagent.core.wcag;

public enum WcagVersion {
    V2_0("2.0"), V2_1("2.1"), V2_2("2.2");

    private final String label;

    WcagVersion(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isAtLeast(WcagVersion other) {
        return ordinal() >= other.ordinal();
    }

    public static WcagVersion parse(String s) {
        for (WcagVersion v : values()) {
            if (v.label.equals(s) || v.name().equalsIgnoreCase(s)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unknown WCAG version: " + s);
    }
}
