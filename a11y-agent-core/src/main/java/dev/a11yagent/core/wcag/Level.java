package dev.a11yagent.core.wcag;

/** WCAG conformance levels. Ordered so that {@code A < AA < AAA}. */
public enum Level {
    A, AA, AAA;

    /** True when this level is included in a target of {@code target} (e.g. AA includes A and AA). */
    public boolean includedIn(Level target) {
        return this.ordinal() <= target.ordinal();
    }
}
