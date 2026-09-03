package dev.a11yagent.core.rules.runtime;

import dev.a11yagent.core.ai.Verdict;
import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.rules.Findings;
import dev.a11yagent.core.rules.RuleContext;
import dev.a11yagent.core.wcag.Wcag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2.4.7 Focus Visible (and evidence for 2.4.13 Focus Appearance): tabs through the page and compares
 * the computed styles of each element in its unfocused and focused state. Elements whose rendering does
 * not change at all fail. Elements that only change subtly (colour/opacity/filter) are passed to the
 * vision model, when configured, to confirm a human can see the indicator.
 */
public final class FocusVisibleRule extends RuntimeRule {

    private static final Set<String> STRONG = Set.of("outlineStyle", "outlineWidth", "outlineColor", "boxShadow",
            "borderTopColor", "borderTopWidth", "borderTopStyle", "borderBottomColor", "backgroundColor", "textDecorationLine",
            "before.outlineStyle", "before.boxShadow", "before.borderTopWidth", "before.backgroundColor", "before.display",
            "after.outlineStyle", "after.boxShadow", "after.borderTopWidth", "after.backgroundColor", "after.display",
            "parent.outlineStyle", "parent.boxShadow", "parent.backgroundColor", "parent.borderTopColor");

    public FocusVisibleRule() {
        super("focus-visible",
                "Every keyboard focus stop has a visible focus indicator (computed-style diff between unfocused and focused state, vision model for ambiguous cases).",
                Set.of(Wcag.get("2.4.7")), Impact.SERIOUS);
    }

    @Override
    public List<Finding> evaluate(RuleContext ctx) {
        KeyboardTraversal.Result t = KeyboardTraversal.of(ctx);
        String url = ctx.driver().url();
        if (t.stops().isEmpty()) {
            return List.of(Findings.inapplicable(id(), criteria(), "No keyboard focusable elements.", url));
        }
        List<Finding> out = new ArrayList<>();
        int shots = 0;
        for (KeyboardTraversal.Stop stop : t.stops()) {
            List<String> changed = visibleChanges(stop);
            Map<String, Object> data = new HashMap<>();
            data.put("changedStyles", changed);
            data.put("outlineStyle", stop.focusedStyles().get("outlineStyle"));
            data.put("outlineWidth", stop.focusedStyles().get("outlineWidth"));
            data.put("boxShadow", stop.focusedStyles().get("boxShadow"));
            if (stop.baselineStyles() == null) {
                out.add(stopFinding(Outcome.CANT_TELL, stop, "Element received focus but was not in the initial tab sequence (dynamic content); focus indicator not compared.", data, url));
                continue;
            }
            boolean strong = changed.stream().anyMatch(STRONG::contains);
            if (strong) {
                out.add(stopFinding(Outcome.PASSED, stop, "Focus indicator present (" + String.join(", ", changed) + ").", data, url));
                continue;
            }
            if (changed.isEmpty()) {
                Finding f = stopFinding(Outcome.FAILED, stop,
                        "No visual change when this element receives keyboard focus (outline: " + stop.focusedStyles().get("outlineStyle") + "). Users cannot see where focus is.", data, url);
                out.add(shots++ < 15 ? withShot(ctx, f) : f);
                continue;
            }
            // Only weak cues changed (colour, opacity, filter, transform): ask the vision model if available.
            if (ctx.judge().isPresent() && ctx.judge().get().hasBudget()) {
                byte[] crop = Findings.elementCrop(ctx, stop.selector(), 24);
                if (crop != null) {
                    // Re-focus the element so the screenshot shows the focused state.
                    ctx.driver().evaluate("(sel) => { const el = document.querySelector(sel); if (el) el.focus({preventScroll:false}); }", stop.selector());
                    byte[] focusedCrop = Findings.elementCrop(ctx, stop.selector(), 24);
                    ctx.inPage().call("blur", null);
                    Verdict v = ctx.judge().get().judge("""
                            Success criterion: WCAG 2.4.7 Focus Visible.
                            Two screenshots of the same control are attached: first WITHOUT keyboard focus, then WITH keyboard focus.
                            Question: Is there a clearly perceivable focus indicator in the second image compared to the first
                            (outline, ring, underline, background change, etc.) that a sighted keyboard user would notice?
                            Element: <%s> "%s". Computed style differences detected: %s.
                            """.formatted(stop.tag(), stop.name(), changed), List.of(crop, focusedCrop == null ? crop : focusedCrop));
                    String shot = ctx.artifacts().savePng("focus-visible-ai", focusedCrop == null ? crop : focusedCrop);
                    data.put("aiVerdict", v.result().name());
                    Evidence ev = new Evidence(shot, v.rationale(), v.model(), v.confidence(), data);
                    Outcome o = switch (v.result()) {
                        case PASS -> Outcome.PASSED;
                        case FAIL -> v.confidence() >= 0.75 ? Outcome.FAILED : Outcome.NEEDS_REVIEW;
                        case UNSURE -> Outcome.NEEDS_REVIEW;
                    };
                    out.add(stopFinding(o, stop, "Focus indicator relies on subtle cues (" + String.join(", ", changed) + "); vision model: " + v.result() + " — " + v.rationale(), data, url).withEvidence(ev));
                    continue;
                }
            }
            Finding f = stopFinding(Outcome.NEEDS_REVIEW, stop,
                    "Focus state only changes " + String.join(", ", changed) + ". Verify the indicator is perceivable (3:1 contrast against the unfocused state, 2.4.13 area requirements at AAA).", data, url);
            out.add(shots++ < 15 ? withShot(ctx, f) : f);
        }
        if (t.truncated()) {
            out.add(pageFinding(Outcome.CANT_TELL, "Tab traversal stopped after " + t.stops().size() + " stops (maxFocusStops); remaining elements were not checked.", Map.of(), url));
        }
        return out;
    }

    /**
     * Style changes that can actually be seen: outline colour/width/offset changes are irrelevant while the
     * outline style is {@code none} (the UA stylesheet still toggles them), and pseudo-element changes are
     * irrelevant while the pseudo-element is not displayed.
     */
    static List<String> visibleChanges(KeyboardTraversal.Stop stop) {
        List<String> changed = new ArrayList<>(stop.changedStyleKeys());
        Map<String, Object> s = stop.focusedStyles();
        Map<String, Object> b = stop.baselineStyles() == null ? Map.of() : stop.baselineStyles();
        if (outlineHidden(s.get("outlineStyle"), s.get("outlineWidth")) && outlineHidden(b.get("outlineStyle"), b.get("outlineWidth"))) {
            changed.removeIf(k -> k.startsWith("outline"));
        }
        for (String pseudo : List.of("before", "after")) {
            if ("none".equals(String.valueOf(s.get(pseudo + ".display"))) && "none".equals(String.valueOf(b.get(pseudo + ".display")))) {
                changed.removeIf(k -> k.startsWith(pseudo + "."));
            }
            if (outlineHidden(s.get(pseudo + ".outlineStyle"), s.get(pseudo + ".outlineWidth")) && outlineHidden(b.get(pseudo + ".outlineStyle"), b.get(pseudo + ".outlineWidth"))) {
                changed.removeIf(k -> k.startsWith(pseudo + ".outline"));
            }
        }
        if (outlineHidden(s.get("parent.outlineStyle"), s.get("parent.outlineWidth")) && outlineHidden(b.get("parent.outlineStyle"), b.get("parent.outlineWidth"))) {
            changed.removeIf(k -> k.startsWith("parent.outline"));
        }
        return changed;
    }

    private static boolean outlineHidden(Object style, Object width) {
        String st = String.valueOf(style);
        String w = String.valueOf(width);
        return "none".equals(st) || "null".equals(st) || "0px".equals(w);
    }

    private Finding withShot(RuleContext ctx, Finding f) {
        ctx.driver().evaluate("(sel) => { const el = document.querySelector(sel); if (el) el.focus(); }", f.target().selector());
        String path = Findings.elementScreenshot(ctx, f.target().selector(), id(), 16);
        ctx.inPage().call("blur", null);
        return path == null ? f : f.withEvidence(f.evidence().withScreenshot(path));
    }
}
