package dev.a11yagent.core.rules.runtime;

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
 * 2.4.11 Focus Not Obscured (Minimum) and 2.4.12 (Enhanced): after each real Tab press, samples the
 * focused element's centre and corners with {@code elementFromPoint} to detect sticky headers, cookie
 * banners, chat widgets or dialogs covering it.
 */
public final class FocusNotObscuredRule extends RuntimeRule {

    private final boolean enhanced;

    private FocusNotObscuredRule(boolean enhanced) {
        super(enhanced ? "focus-not-obscured-enhanced" : "focus-not-obscured-minimum",
                enhanced ? "No part of a focused element is hidden by author-created content (AAA)."
                        : "A focused element is not entirely hidden by author-created content such as sticky headers, banners or chat widgets.",
                Set.of(Wcag.get(enhanced ? "2.4.12" : "2.4.11")),
                enhanced ? Impact.MODERATE : Impact.SERIOUS);
        this.enhanced = enhanced;
    }

    public static FocusNotObscuredRule minimum() {
        return new FocusNotObscuredRule(false);
    }

    public static FocusNotObscuredRule enhanced() {
        return new FocusNotObscuredRule(true);
    }

    @Override
    public List<Finding> evaluate(RuleContext ctx) {
        KeyboardTraversal.Result t = KeyboardTraversal.of(ctx);
        String url = ctx.driver().url();
        if (t.stops().isEmpty()) {
            return List.of(Findings.inapplicable(id(), criteria(), "No keyboard focusable elements.", url));
        }
        List<Finding> out = new ArrayList<>();
        int issues = 0;
        for (KeyboardTraversal.Stop s : t.stops()) {
            Map<String, Object> ob = s.obscured();
            if (ob == null) {
                continue;
            }
            boolean fully = Boolean.TRUE.equals(ob.get("fully"));
            boolean partially = Boolean.TRUE.equals(ob.get("partially"));
            Map<String, Object> data = new HashMap<>(ob);
            if (fully || (enhanced && partially)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> by = (Map<String, Object>) ob.get("by");
                String bySel = by == null ? "another element" : String.valueOf(by.get("selector"));
                Finding f = stopFinding(Outcome.FAILED, s,
                        (fully ? "Focused element is entirely covered by " : "Focused element is partially covered by ") + bySel
                                + (by != null && by.get("position") != null ? " (position: " + by.get("position") + ")" : "") + ".", data, url);
                if (issues++ < 10) {
                    ctx.driver().evaluate("(sel) => { const el = document.querySelector(sel); if (el) el.focus(); }", s.selector());
                    String shot = Findings.elementScreenshot(ctx, s.selector(), id(), 40);
                    ctx.inPage().call("blur", null);
                    if (shot != null) {
                        f = f.withEvidence(f.evidence().withScreenshot(shot));
                    }
                }
                out.add(f);
            } else {
                out.add(stopFinding(Outcome.PASSED, s, partially ? "Focused element is partially covered (allowed at AA)." : "Focused element is not obscured.", data, url));
            }
        }
        return out.isEmpty() ? List.of(Findings.inapplicable(id(), criteria(), "No measurable focus stops.", url)) : out;
    }
}
