package dev.a11yagent.core.rules;

import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.wcag.Criterion;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A rule implemented in the in-page JavaScript bundle; this class only carries WCAG metadata. */
public class InPageRule implements Rule {

    private final String id;
    private final String description;
    private final Set<Criterion> criteria;
    private final Impact impact;

    public InPageRule(String id, String description, Set<Criterion> criteria, Impact impact) {
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

    public Impact impact() {
        return impact;
    }

    @Override
    public RuleKind kind() {
        return RuleKind.IN_PAGE;
    }

    @Override
    public List<Finding> evaluate(RuleContext ctx) {
        List<Map<String, Object>> raw = ctx.inPage().runRule(id, Map.of());
        List<Finding> findings = Findings.fromRaw(id, criteria, impact, raw, ctx.driver().url());
        return Findings.attachScreenshots(ctx, postProcess(ctx, findings));
    }

    /** Hook for subclasses (e.g. AI-assisted rules). */
    protected List<Finding> postProcess(RuleContext ctx, List<Finding> findings) {
        return findings;
    }
}
