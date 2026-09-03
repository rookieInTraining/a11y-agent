package dev.a11yagent.core.benchmark;

import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import java.util.List;

/** Aggregates the findings selected for a test case into one outcome and scores it against ACT. */
public final class ActScorer {

    private ActScorer() {
    }

    /**
     * Page-level outcome for a test case: a failure anywhere dominates, then abstention, then a pass;
     * with nothing selected the rule did not apply.
     */
    public static Outcome aggregate(List<Finding> selected) {
        boolean abstained = false;
        boolean passed = false;
        for (Finding f : selected) {
            switch (f.outcome()) {
                case FAILED -> {
                    return Outcome.FAILED;
                }
                case NEEDS_REVIEW, CANT_TELL -> abstained = true;
                case PASSED -> passed = true;
                case INAPPLICABLE -> { }
            }
        }
        return abstained ? Outcome.CANT_TELL : passed ? Outcome.PASSED : Outcome.INAPPLICABLE;
    }

    public static ActVerdict verdict(String expected, Outcome actual) {
        boolean reportedFailure = actual == Outcome.FAILED;
        if ("failed".equals(expected)) {
            if (reportedFailure) {
                return ActVerdict.CORRECT;
            }
            return actual == Outcome.CANT_TELL ? ActVerdict.ABSTAINED : ActVerdict.FALSE_NEGATIVE;
        }
        // passed / inapplicable: the requirement is simply not to report a failure
        return reportedFailure ? ActVerdict.FALSE_POSITIVE : ActVerdict.CORRECT;
    }
}
