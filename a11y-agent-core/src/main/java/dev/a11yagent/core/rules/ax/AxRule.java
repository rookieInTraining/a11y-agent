package dev.a11yagent.core.rules.ax;

import dev.a11yagent.core.ax.AxNode;
import dev.a11yagent.core.ax.AxTree;
import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.Target;
import dev.a11yagent.core.rules.Rule;
import dev.a11yagent.core.rules.RuleContext;
import dev.a11yagent.core.rules.RuleKind;
import dev.a11yagent.core.wcag.Criterion;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Base class for rules evaluated on the browser accessibility tree. */
public abstract class AxRule implements Rule {

    private final String id;
    private final String description;
    private final Set<Criterion> criteria;
    protected final Impact impact;

    protected AxRule(String id, String description, Set<Criterion> criteria, Impact impact) {
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
        return RuleKind.AX_TREE;
    }

    @Override
    public List<Finding> evaluate(RuleContext ctx) {
        return ctx.axTree()
                .map(tree -> evaluate(ctx, tree))
                .orElseGet(() -> List.of(finding(Outcome.CANT_TELL, Target.page(), "Accessibility tree not available from this driver; rule skipped.", Map.of(), ctx.driver().url())));
    }

    protected abstract List<Finding> evaluate(RuleContext ctx, AxTree tree);

    protected Finding finding(Outcome outcome, Target target, String message, Map<String, Object> data, String url) {
        return Finding.builder(id).criteria(criteria).outcome(outcome).impact(impact).message(message)
                .target(target).evidence(Evidence.deterministic(message, data)).url(url).build();
    }

    protected Finding nodeFinding(Outcome outcome, AxTree tree, AxNode node, String message, Map<String, Object> data, String url) {
        return finding(outcome, tree.target(node), message, data, url);
    }

    protected static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
