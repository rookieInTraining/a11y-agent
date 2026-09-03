package dev.a11yagent.core.rules.ax;

import dev.a11yagent.core.ax.AxNode;
import dev.a11yagent.core.ax.AxTree;
import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.Target;
import dev.a11yagent.core.rules.Findings;
import dev.a11yagent.core.rules.InPageRule;
import dev.a11yagent.core.rules.RuleContext;
import dev.a11yagent.core.rules.RuleKind;
import dev.a11yagent.core.wcag.Criterion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Accessible-name rules evaluated on the browser accessibility tree, which is the only authoritative
 * source: it applies the full accname algorithm, presentational-role conflict resolution and the
 * name-from-content rules per role. The in-page DOM heuristics still run so the rule works without a
 * DevTools session (browser extension, non-Chromium driver), but when the tree is available its verdict
 * wins.
 *
 * <p>Each AX role is mapped to a sub-check so one rule can answer several ACT rules precisely.
 */
public final class AxNameRule extends InPageRule {

    /** AX role to sub-check, matching the checks the in-page rules emit. */
    private final Map<String, String> checkByRole;

    public AxNameRule(String id, String description, Set<Criterion> criteria, Impact impact,
                      Map<String, String> checkByRole) {
        super(id, description, criteria, impact);
        this.checkByRole = Map.copyOf(checkByRole);
    }

    @Override
    public RuleKind kind() {
        return RuleKind.AX_TREE;
    }

    @Override
    protected List<Finding> postProcess(RuleContext ctx, List<Finding> domFindings) {
        if (ctx.axTree().isEmpty()) {
            return domFindings;
        }
        AxTree tree = ctx.axTree().get();
        String url = ctx.driver().url();
        List<Finding> out = new ArrayList<>();
        for (AxNode n : tree.nodes()) {
            if (n.ignored()) {
                continue;
            }
            String check = checkByRole.get(n.role());
            if (check == null) {
                continue;
            }
            Target target = tree.target(n);
            check = refine(check, target);
            if (check == null) {
                continue;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("check", check);
            data.put("role", n.role());
            data.put("name", n.name());
            data.put("source", "accessibility-tree");
            if (n.hasName()) {
                String msg = describe(n.role()) + " is exposed with the accessible name \"" + abbreviate(n.name()) + "\".";
                out.add(finding(Outcome.PASSED, target, msg, data, url));
            } else {
                String msg = describe(n.role()) + " is exposed to assistive technology with an empty accessible name. " + hint(n.role());
                out.add(finding(Outcome.FAILED, target, msg, data, url));
            }
        }
        // the tree is authoritative for the roles it covers; DOM findings for other checks are kept
        Set<String> covered = Set.copyOf(checkByRole.values());
        for (Finding f : domFindings) {
            Object check = f.evidence().data().get("check");
            if (check == null || !covered.contains(check)) {
                out.add(f);
            }
        }
        return Findings.attachScreenshots(ctx, out);
    }

    /**
     * The accessibility tree reports one role for elements that the guidelines treat separately: an
     * {@code <svg role="img">}, an {@code <img>} and an {@code <input type="image">} are all exposed as
     * an image or a button. The element decides which check the finding belongs to. Returns null when
     * the element is out of scope for this rule.
     */
    private static String refine(String check, Target target) {
        String html = target.html() == null ? "" : target.html().toLowerCase(Locale.ROOT);
        if (html.startsWith("<svg")) {
            // only an explicit role puts an SVG in scope of the SVG naming rule
            return html.contains("role=") ? "svg-name" : null;
        }
        if (html.startsWith("<object")) {
            return "object-name";
        }
        if (html.startsWith("<input")) {
            return html.contains("type=\"image\"") || html.contains("type='image'") ? "image-button-name" : check;
        }
        if (html.startsWith("<iframe") || html.startsWith("<frame")) {
            // a frame taken out of the tab order is not part of the sequential reading experience
            return html.contains("tabindex=\"-1\"") ? null : "iframe-name";
        }
        return check;
    }

    private Finding finding(Outcome outcome, Target target, String message, Map<String, Object> data, String url) {
        return Finding.builder(id()).criteria(criteria()).outcome(outcome).impact(impact()).message(message)
                .target(target).evidence(Evidence.deterministic(message, data)).url(url).build();
    }

    private static String describe(String role) {
        return switch (role) {
            case "link" -> "Link";
            case "button" -> "Button";
            case "Iframe", "iframe" -> "Frame";
            case "image", "img" -> "Image";
            case "heading" -> "Heading";
            case "menuitem", "menuitemcheckbox", "menuitemradio" -> "Menu item";
            case "DisclosureTriangle" -> "Summary element";
            case "textbox", "searchbox", "combobox", "listbox", "slider", "spinbutton", "checkbox", "radio", "switch" -> "Form field";
            default -> "Control (role " + role + ")";
        };
    }

    private static String hint(String role) {
        return switch (role) {
            case "link", "button", "menuitem", "menuitemcheckbox", "menuitemradio" -> "Add visible text, an aria-label, or alt text on the image it contains.";
            case "image", "img" -> "Add alt text, aria-label or aria-labelledby (or mark it decorative with alt=\"\").";
            case "Iframe", "iframe" -> "Add a title attribute describing the embedded content.";
            case "heading" -> "Give the heading text content that is exposed to assistive technology.";
            default -> "Associate a label with it, or use aria-label/aria-labelledby.";
        };
    }

    private static String abbreviate(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
