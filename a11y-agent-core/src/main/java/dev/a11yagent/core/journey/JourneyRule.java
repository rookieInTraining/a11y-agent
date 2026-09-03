package dev.a11yagent.core.journey;

import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.Target;
import dev.a11yagent.core.wcag.Criterion;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A rule evaluated across all page states of a journey. */
public abstract class JourneyRule {

    private final String id;
    private final String description;
    private final Set<Criterion> criteria;
    protected final Impact impact;

    protected JourneyRule(String id, String description, Set<Criterion> criteria, Impact impact) {
        this.id = id;
        this.description = description;
        this.criteria = Set.copyOf(criteria);
        this.impact = impact;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public Set<Criterion> criteria() {
        return criteria;
    }

    public abstract List<Finding> evaluate(List<StepSnapshot> snapshots);

    protected Finding finding(Outcome outcome, StepSnapshot snap, String selector, String message, Map<String, Object> data) {
        return Finding.builder(id)
                .criteria(criteria)
                .outcome(outcome)
                .impact(impact)
                .message(message)
                .target(new Target(selector == null ? "html" : selector, "", null))
                .evidence(Evidence.deterministic(message, data))
                .step(snap == null ? null : snap.step())
                .url(snap == null ? null : snap.url())
                .build();
    }

    protected Finding inapplicable(String message) {
        return finding(Outcome.INAPPLICABLE, null, null, message, Map.of());
    }

    protected Finding passed(String message, Map<String, Object> data) {
        return finding(Outcome.PASSED, null, null, message, data);
    }
}
