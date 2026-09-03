package dev.a11yagent.core.benchmark;

/**
 * Result of comparing our outcome for a test case with the outcome ACT expects.
 *
 * <p>Follows the W3C notion of implementation consistency: for a {@code failed} test case the
 * implementation must report a failure; for {@code passed} and {@code inapplicable} test cases it must
 * not report a failure. Abstaining on a {@code failed} case is not a wrong answer but it is not a
 * correct one either, so it is tracked separately and counted against accuracy.
 */
public enum ActVerdict {
    /** Expected failure and we reported one, or expected pass/inapplicable and we did not report a failure. */
    CORRECT,
    /** Expected pass or inapplicable but we reported a failure. */
    FALSE_POSITIVE,
    /** Expected a failure and we definitively reported pass/inapplicable. */
    FALSE_NEGATIVE,
    /** Expected a failure and we abstained (needs review / can't tell). */
    ABSTAINED,
    /** No rule of ours is mapped to this ACT rule. */
    OUT_OF_SCOPE;

    public boolean isWrong() {
        return this == FALSE_POSITIVE || this == FALSE_NEGATIVE;
    }

    public boolean counts() {
        return this != OUT_OF_SCOPE;
    }
}
