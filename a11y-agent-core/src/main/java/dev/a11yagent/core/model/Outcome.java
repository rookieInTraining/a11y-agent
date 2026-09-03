package dev.a11yagent.core.model;

/**
 * Rule outcome vocabulary, aligned with W3C EARL / ACT Rules so results can be mapped to
 * conformance claims in a VPAT without ambiguity.
 */
public enum Outcome {
    /** The test target satisfies the rule. */
    PASSED,
    /** The test target violates the rule. */
    FAILED,
    /** Nothing on the page is a target for this rule. */
    INAPPLICABLE,
    /** The rule could not determine an outcome automatically. */
    CANT_TELL,
    /** A heuristic or AI judgement produced a probable issue that a human auditor must confirm. */
    NEEDS_REVIEW;

    public boolean isIssue() {
        return this == FAILED || this == NEEDS_REVIEW;
    }
}
