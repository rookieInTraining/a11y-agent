package dev.a11yagent.core.rules.ax;

import dev.a11yagent.core.ax.AxNode;
import dev.a11yagent.core.ax.AxTree;
import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.Target;
import dev.a11yagent.core.rules.InPageRule;
import dev.a11yagent.core.rules.RuleContext;
import dev.a11yagent.core.rules.RuleKind;
import dev.a11yagent.core.wcag.Criterion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * DOM heuristics produce the first pass (extension-portable); when the browser accessibility tree is
 * available its computed names are authoritative: DOM failures the browser does name are downgraded, and
 * unnamed nodes the DOM pass missed are added.
 */
public class AxReconciledNameRule extends InPageRule {

    private static final int MAX_RESOLVED_NODES = 400;

    private final Predicate<AxNode> nodeFilter;
    private final String whatUnnamed;

    public AxReconciledNameRule(String id, String description, Set<Criterion> criteria, Impact impact,
                                Predicate<AxNode> nodeFilter, String whatUnnamed) {
        super(id, description, criteria, impact);
        this.nodeFilter = nodeFilter;
        this.whatUnnamed = whatUnnamed;
    }

    @Override
    public RuleKind kind() {
        return RuleKind.AX_TREE;
    }

    @Override
    protected List<Finding> postProcess(RuleContext ctx, List<Finding> findings) {
        if (ctx.axTree().isEmpty()) {
            return findings;
        }
        AxTree tree = ctx.axTree().get();
        String url = ctx.driver().url();
        Map<String, AxNode> bySelector = new HashMap<>();
        int resolved = 0;
        for (AxNode n : tree.nodes()) {
            if (n.ignored() || !nodeFilter.test(n)) {
                continue;
            }
            if (resolved++ >= MAX_RESOLVED_NODES) {
                break;
            }
            Target t = tree.target(n);
            if (t != null && !t.selector().startsWith("(ax:")) {
                bySelector.putIfAbsent(t.selector(), n);
            }
        }

        List<Finding> out = new ArrayList<>(findings.size());
        Set<String> domSelectors = new HashSet<>();
        for (Finding f : findings) {
            domSelectors.add(f.target().selector());
            AxNode ax = bySelector.get(f.target().selector());
            if (f.outcome() == Outcome.FAILED && ax != null && ax.hasName()) {
                Map<String, Object> data = new LinkedHashMap<>(f.evidence().data());
                data.put("browserName", ax.name());
                data.put("browserRole", ax.role());
                out.add(f.withOutcome(Outcome.PASSED, "Browser computes the accessible name \"" + abbreviate(ax.name()) + "\" (role " + ax.role() + "); DOM heuristic could not derive it.")
                        .withEvidence(new Evidence(null, "Reconciled against the browser accessibility tree.", null, 1.0, data)));
            } else {
                out.add(f);
            }
        }
        for (Map.Entry<String, AxNode> e : bySelector.entrySet()) {
            AxNode n = e.getValue();
            if (n.hasName() || domSelectors.contains(e.getKey())) {
                continue;
            }
            Target t = tree.target(n);
            String message = whatUnnamed + " is exposed to assistive technology as role \"" + n.role() + "\" with an empty accessible name (browser accessibility tree).";
            Map<String, Object> data = Map.of("browserRole", String.valueOf(n.role()), "focusable", n.focusable(), "source", "ax-tree");
            out.add(Finding.builder(id()).criteria(criteria()).outcome(Outcome.FAILED).impact(impact())
                    .message(message).target(t).evidence(Evidence.deterministic(message, data)).url(url).build());
        }
        return out;
    }

    private static String abbreviate(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
