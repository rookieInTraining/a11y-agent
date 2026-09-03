package dev.a11yagent.benchmark.act;

import java.net.URI;
import java.util.Set;

/**
 * One ACT Rules test case.
 *
 * @param ruleId       ACT rule id, e.g. "674b10"
 * @param ruleName     human readable rule name
 * @param testcaseId   hash identifying this revision of the case
 * @param url          canonical w3.org URL
 * @param relativePath path below {@code /WAI/content-assets/wcag-act-rules/}
 * @param expected     expected outcome
 * @param requirements accessibility requirements the rule maps to (e.g. "wcag20:4.1.2")
 */
public record ActTestCase(String ruleId, String ruleName, String testcaseId, String url, String relativePath,
                          Expected expected, Set<String> requirements) {

    public enum Expected {
        PASSED, FAILED, INAPPLICABLE;

        public static Expected parse(String s) {
            return switch (s) {
                case "passed" -> PASSED;
                case "failed" -> FAILED;
                case "inapplicable" -> INAPPLICABLE;
                default -> throw new IllegalArgumentException("Unknown expected outcome: " + s);
            };
        }
    }

    public String fileName() {
        return URI.create(url).getPath().replaceAll(".*/", "");
    }

    /** Short label for logs: rule id + case file prefix. */
    public String label() {
        String f = fileName();
        return ruleId + "/" + (f.length() > 10 ? f.substring(0, 10) : f);
    }
}
