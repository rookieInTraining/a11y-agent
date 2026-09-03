package dev.a11yagent.core.benchmark;

import java.util.List;

/**
 * One W3C ACT Rules test case.
 *
 * @param ruleId     ACT rule identifier, e.g. {@code 674b10}
 * @param ruleName   human readable rule name
 * @param testcaseId hash identifying the case
 * @param expected   {@code passed}, {@code failed} or {@code inapplicable}
 * @param url        canonical URL of the case on w3.org
 * @param path       URL path, used to resolve the case in a local mirror of the corpus
 * @param criteria   WCAG success criteria the rule maps to
 */
public record ActCase(String ruleId, String ruleName, String testcaseId, String expected, String url, String path,
                      List<String> criteria) {

    public ActCase {
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
    }

    public boolean expectsFailure() {
        return "failed".equals(expected);
    }
}
