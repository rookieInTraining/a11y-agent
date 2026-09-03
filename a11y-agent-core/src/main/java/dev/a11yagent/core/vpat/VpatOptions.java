package dev.a11yagent.core.vpat;

import java.time.LocalDate;

/**
 * Report metadata for the ACR header.
 *
 * @param productName      name of the product (and version)
 * @param productDescription short description
 * @param vendor           company / team producing the report
 * @param evaluator        who ran the evaluation
 * @param contact          contact information
 * @param notes            free-text notes (scope, exclusions)
 * @param evaluationMethods description of methods used
 * @param date             report date
 */
public record VpatOptions(
        String productName,
        String productDescription,
        String vendor,
        String evaluator,
        String contact,
        String notes,
        String evaluationMethods,
        LocalDate date) {

    public static VpatOptions forProduct(String productName) {
        return new VpatOptions(productName, "", "", "a11y-agent (automated) — pending human review", "", "",
                "Automated testing with a11y-agent (Playwright-driven runtime probes, in-page heuristics, and vision-model judgements), "
                        + "to be complemented by manual testing with assistive technologies.", LocalDate.now());
    }
}
