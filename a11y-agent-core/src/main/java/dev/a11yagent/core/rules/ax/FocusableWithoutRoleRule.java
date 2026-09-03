package dev.a11yagent.core.rules.ax;

import dev.a11yagent.core.ax.AxNode;
import dev.a11yagent.core.ax.AxTree;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.rules.Findings;
import dev.a11yagent.core.rules.RuleContext;
import dev.a11yagent.core.wcag.Wcag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 4.1.2 Name, Role, Value via the accessibility tree: an element that is keyboard focusable but is
 * exposed as a generic container has no role and usually no name, so a screen reader announces nothing
 * useful (typically a {@code <div tabindex="0">} used as a control).
 */
public final class FocusableWithoutRoleRule extends AxRule {

    private static final Set<String> ALLOWED_GENERIC_FOCUS = Set.of("RootWebArea", "Iframe", "IframePresentational", "scrollbar", "document", "application");

    public FocusableWithoutRoleRule() {
        super("ax-focusable-without-role",
                "Keyboard-focusable elements expose a role to assistive technology (browser accessibility tree).",
                Set.of(Wcag.get("4.1.2")), Impact.SERIOUS);
    }

    @Override
    protected List<Finding> evaluate(RuleContext ctx, AxTree tree) {
        String url = ctx.driver().url();
        List<Finding> out = new ArrayList<>();
        long focusable = 0;
        for (AxNode n : tree.nodes()) {
            if (!n.focusable() || n.ignored()) {
                continue;
            }
            focusable++;
            if (!n.isGeneric() || ALLOWED_GENERIC_FOCUS.contains(n.role())) {
                continue;
            }
            // A generic wrapper whose only child is a real control is a common pattern; report the wrapper itself
            String text = abbreviate(tree.textOf(n), 60);
            Map<String, Object> data = Map.of("role", String.valueOf(n.role()), "name", String.valueOf(n.name()), "text", text);
            out.add(nodeFinding(Outcome.FAILED, tree, n,
                    "Focusable element is exposed with role \"" + n.role() + "\"" + (n.hasName() ? " and name \"" + abbreviate(n.name(), 60) + "\" (names on generic elements are not reliably announced)" : " and no name")
                            + ". Screen readers announce " + (text.isEmpty() ? "nothing" : "only the inner text \"" + text + "\"") + " with no role. Use a native control or add an appropriate role.",
                    data, url));
        }
        if (focusable == 0) {
            return List.of(Findings.inapplicable(id(), criteria(), "No focusable nodes in the accessibility tree.", url));
        }
        if (out.isEmpty()) {
            out.add(finding(Outcome.PASSED, dev.a11yagent.core.model.Target.page(), focusable + " focusable node(s) all expose a role.", Map.of("focusable", focusable), url));
        }
        return Findings.attachScreenshots(ctx, out);
    }
}
