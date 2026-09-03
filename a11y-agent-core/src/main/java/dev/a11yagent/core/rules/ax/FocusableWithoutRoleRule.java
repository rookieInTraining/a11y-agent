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

    private static final Set<String> ALLOWED_FOCUS = Set.of("RootWebArea", "Iframe", "IframePresentational", "scrollbar", "document", "application",
            "dialog", "alertdialog", "tabpanel", "region", "main", "article", "group", "toolbar", "menu", "menubar", "tablist", "tree", "grid", "treegrid", "listbox", "radiogroup", "table", "Canvas", "video", "audio", "Video", "Audio", "EmbeddedObject", "PluginObject", "Section", "textbox", "searchbox", "combobox", "slider", "spinbutton", "Details");
    /** Roles that are static content; a focusable one masquerades as a control (e.g. {@code <h2 tabindex="0">} used as a skip link). */
    private static final Set<String> STATIC_ROLES = Set.of("heading", "paragraph", "StaticText", "image", "list", "listitem", "cell", "row", "term", "definition",
            "blockquote", "figure", "caption", "LabelText", "code", "emphasis", "strong", "time", "mark", "deletion", "insertion", "subscript", "superscript", "note", "DescriptionList", "DescriptionListTerm", "DescriptionListDetail");

    public FocusableWithoutRoleRule() {
        super("ax-focusable-without-role",
                "Elements in the tab order expose an interactive role to assistive technology (browser accessibility tree): no focusable generic containers, no focusable headings/paragraphs/images acting as controls.",
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
            boolean generic = n.isGeneric();
            boolean staticRole = STATIC_ROLES.contains(n.role());
            if ((!generic && !staticRole) || ALLOWED_FOCUS.contains(n.role())) {
                continue;
            }
            dev.a11yagent.core.model.Target target = tree.target(n);
            if (target.html() != null && target.html().matches("(?s)^<[^>]*\\btabindex=\"-1\"[^>]*>.*")) {
                continue; // programmatic focus target (skip-link destination, dialog container, carousel slide), not in the tab order
            }
            String text = abbreviate(tree.textOf(n), 60);
            Map<String, Object> data = Map.of("role", String.valueOf(n.role()), "name", String.valueOf(n.name()), "text", text);
            if (generic) {
                out.add(nodeFinding(Outcome.FAILED, tree, n,
                        "Focusable element is exposed with role \"" + n.role() + "\"" + (n.hasName() ? " and name \"" + abbreviate(n.name(), 60) + "\" (names on generic elements are not reliably announced)" : " and no name")
                                + ". Screen readers announce " + (text.isEmpty() ? "nothing" : "only the inner text \"" + text + "\"") + " with no role. Use a native control or add an appropriate role.",
                        data, url));
            } else {
                out.add(nodeFinding(Outcome.FAILED, tree, n,
                        "Element in the tab order is exposed as \"" + n.role() + "\"" + (n.hasName() ? " \"" + abbreviate(n.name(), 60) + "\"" : "")
                                + ", a static role. Screen reader users hear a " + n.role() + ", not something they can activate. Use a link/button or the matching widget role.",
                        data, url));
            }
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
