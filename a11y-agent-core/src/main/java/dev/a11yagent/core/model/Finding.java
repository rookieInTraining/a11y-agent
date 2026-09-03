package dev.a11yagent.core.model;

import dev.a11yagent.core.wcag.Criterion;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A single rule result against a single target.
 *
 * @param ruleId   rule that produced the finding
 * @param criteria WCAG success criteria the rule maps to
 * @param outcome  ACT-style outcome
 * @param impact   severity, meaningful only for FAILED/NEEDS_REVIEW
 * @param message  human readable summary
 * @param target   element (or page) the finding refers to
 * @param evidence supporting evidence
 * @param step     journey step name during which the finding was produced (null for single page audits)
 * @param url      page URL at the time of the finding
 */
public record Finding(
        String ruleId,
        Set<Criterion> criteria,
        Outcome outcome,
        Impact impact,
        String message,
        Target target,
        Evidence evidence,
        String step,
        String url) {

    public Finding {
        criteria = criteria == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(criteria));
    }

    public Finding inStep(String stepName, String pageUrl) {
        return new Finding(ruleId, criteria, outcome, impact, message, target, evidence, stepName, pageUrl);
    }

    public Finding withEvidence(Evidence e) {
        return new Finding(ruleId, criteria, outcome, impact, message, target, e, step, url);
    }

    public Finding withOutcome(Outcome o, String newMessage) {
        return new Finding(ruleId, criteria, o, impact, newMessage, target, evidence, step, url);
    }

    public static Builder builder(String ruleId) {
        return new Builder(ruleId);
    }

    public static final class Builder {
        private final String ruleId;
        private Set<Criterion> criteria = Set.of();
        private Outcome outcome = Outcome.FAILED;
        private Impact impact = Impact.MODERATE;
        private String message = "";
        private Target target = Target.page();
        private Evidence evidence = Evidence.deterministic("");
        private String step;
        private String url;

        private Builder(String ruleId) {
            this.ruleId = ruleId;
        }

        public Builder criteria(Set<Criterion> c) { this.criteria = c; return this; }
        public Builder outcome(Outcome o) { this.outcome = o; return this; }
        public Builder impact(Impact i) { this.impact = i; return this; }
        public Builder message(String m) { this.message = m; return this; }
        public Builder target(Target t) { this.target = t; return this; }
        public Builder evidence(Evidence e) { this.evidence = e; return this; }
        public Builder step(String s) { this.step = s; return this; }
        public Builder url(String u) { this.url = u; return this; }

        public Finding build() {
            return new Finding(ruleId, criteria, outcome, impact, message, target, evidence, step, url);
        }
    }
}
