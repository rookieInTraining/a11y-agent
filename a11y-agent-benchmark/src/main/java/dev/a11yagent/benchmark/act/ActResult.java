package dev.a11yagent.benchmark.act;

import dev.a11yagent.core.model.Outcome;
import java.util.List;

/**
 * Outcome of running the mapped rules against one test case.
 *
 * @param testCase the case
 * @param actual   aggregated outcome
 * @param messages messages of the findings that drove the outcome (for triage)
 * @param rulesRun a11y-agent rules that were executed
 */
public record ActResult(ActTestCase testCase, Outcome actual, List<String> messages, List<String> rulesRun) {

    /**
     * Practical correctness: real violations must be reported as failures, and cases that pass or are
     * out of scope must not be reported as failures. This is the metric that reflects whether a tool is
     * useful — it penalises both misses and false positives.
     */
    public boolean correct() {
        return switch (testCase.expected()) {
            case FAILED -> actual == Outcome.FAILED;
            case PASSED, INAPPLICABLE -> actual != Outcome.FAILED;
        };
    }

    /**
     * Strict outcome equality, where {@code cantTell}/{@code needsReview} counts as wrong and
     * {@code inapplicable} is accepted for an expected {@code passed} case only when the rule has no
     * applicable target at all. Reported alongside {@link #correct()} for transparency.
     */
    public boolean exact() {
        return switch (testCase.expected()) {
            case FAILED -> actual == Outcome.FAILED;
            case PASSED -> actual == Outcome.PASSED;
            case INAPPLICABLE -> actual == Outcome.INAPPLICABLE;
        };
    }

    public String kind() {
        if (correct()) {
            return exact() ? "correct" : "correct-inexact";
        }
        return testCase.expected() == ActTestCase.Expected.FAILED ? "false-negative" : "false-positive";
    }
}
