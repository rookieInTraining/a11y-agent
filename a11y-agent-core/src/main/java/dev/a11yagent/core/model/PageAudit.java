package dev.a11yagent.core.model;

import java.util.List;

/**
 * Findings collected for one page state (one journey step, or the single page of a plain audit).
 *
 * @param step       journey step name ("page" for single-page audits)
 * @param url        URL at audit time
 * @param title      document title at audit time
 * @param screenshot relative path of the full-page screenshot, may be null
 * @param findings   all findings for this page state
 */
public record PageAudit(String step, String url, String title, String screenshot, List<Finding> findings) {

    public PageAudit {
        findings = List.copyOf(findings);
    }

    public long count(Outcome outcome) {
        return findings.stream().filter(f -> f.outcome() == outcome).count();
    }
}
