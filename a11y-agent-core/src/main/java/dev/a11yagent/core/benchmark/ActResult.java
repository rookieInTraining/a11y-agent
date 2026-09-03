package dev.a11yagent.core.benchmark;

import dev.a11yagent.core.model.Outcome;
import java.util.List;

/**
 * Outcome of running our implementation against one ACT test case.
 *
 * @param testCase  the case
 * @param claimed   whether the ACT rule is in our claimed (fully automated) set
 * @param actual    aggregated outcome of the selected findings
 * @param verdict   comparison with the expected outcome
 * @param selectors selectors that were evaluated
 * @param messages  messages of the selected findings, for triage
 */
public record ActResult(ActCase testCase, boolean claimed, Outcome actual, ActVerdict verdict,
                        List<String> selectors, List<String> messages) {

    public ActResult {
        selectors = List.copyOf(selectors);
        messages = List.copyOf(messages);
    }

    public static ActResult outOfScope(ActCase c) {
        return new ActResult(c, false, Outcome.INAPPLICABLE, ActVerdict.OUT_OF_SCOPE, List.of(), List.of());
    }
}
