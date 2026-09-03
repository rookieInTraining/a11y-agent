package dev.a11yagent.core.rules.runtime;

import dev.a11yagent.core.driver.Rect;
import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.Target;
import dev.a11yagent.core.rules.Rule;
import dev.a11yagent.core.rules.RuleKind;
import dev.a11yagent.core.wcag.Criterion;
import java.util.Map;
import java.util.Set;

/** Base class for rules that need real user-agent behaviour. */
public abstract class RuntimeRule implements Rule {

    private final String id;
    private final String description;
    private final Set<Criterion> criteria;
    protected final Impact impact;

    protected RuntimeRule(String id, String description, Set<Criterion> criteria, Impact impact) {
        this.id = id;
        this.description = description;
        this.criteria = Set.copyOf(criteria);
        this.impact = impact;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Set<Criterion> criteria() {
        return criteria;
    }

    @Override
    public RuleKind kind() {
        return RuleKind.RUNTIME;
    }

    protected Finding finding(Outcome outcome, String selector, String html, Rect rect, String message, Map<String, Object> data, String url) {
        return Finding.builder(id)
                .criteria(criteria)
                .outcome(outcome)
                .impact(impact)
                .message(message)
                .target(new Target(selector == null ? "html" : selector, html == null ? "" : html, rect))
                .evidence(Evidence.deterministic(message, data))
                .url(url)
                .build();
    }

    protected Finding pageFinding(Outcome outcome, String message, Map<String, Object> data, String url) {
        return finding(outcome, "html", "", null, message, data, url);
    }

    protected Finding stopFinding(Outcome outcome, KeyboardTraversal.Stop stop, String message, Map<String, Object> data, String url) {
        return finding(outcome, stop.selector(), stop.html(), stop.rect(), message, data, url);
    }
}
