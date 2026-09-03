package dev.a11yagent.core.vpat;

import dev.a11yagent.core.wcag.Criterion;
import dev.a11yagent.core.wcag.Level;

/**
 * One row of the WCAG conformance table.
 *
 * @param criterion   the success criterion
 * @param level       level in the report's WCAG version
 * @param conformance conformance term
 * @param remarks     "Remarks and Explanations" column
 * @param failed      number of failed findings
 * @param review      number of findings needing human review
 * @param passed      number of passed findings
 * @param automated   whether any automated rule covers the criterion
 */
public record VpatEntry(Criterion criterion, Level level, Conformance conformance, String remarks,
                        long failed, long review, long passed, boolean automated) {
}
