package dev.a11yagent.core.rules.runtime;

import dev.a11yagent.core.driver.Viewport;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.rules.RuleContext;
import dev.a11yagent.core.wcag.Wcag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.4.10 Reflow: resizes the viewport to 320 CSS px wide and checks for two-dimensional scrolling.
 * Elements that legitimately need 2D layout (tables, images, video, code) are reported for review
 * instead of failed.
 */
public final class ReflowRule extends RuntimeRule {

    public static final int REFLOW_WIDTH = 320;

    public ReflowRule() {
        super("reflow",
                "Content reflows to a 320 CSS px wide viewport without horizontal scrolling (except content that requires two-dimensional layout).",
                Set.of(Wcag.get("1.4.10")), Impact.SERIOUS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Finding> evaluate(RuleContext ctx) {
        var driver = ctx.driver();
        String url = driver.url();
        Viewport original = driver.viewport();
        List<Finding> out = new ArrayList<>();
        try {
            driver.setViewport(new Viewport(REFLOW_WIDTH, Math.max(480, original.height())));
            driver.waitMillis(400);
            Map<String, Object> res = (Map<String, Object>) ctx.inPage().call("horizontalOverflow", null);
            boolean overflow = Boolean.TRUE.equals(res.get("overflow"));
            List<Map<String, Object>> offenders = (List<Map<String, Object>>) res.get("offenders");
            if (!overflow) {
                out.add(pageFinding(Outcome.PASSED, "No horizontal scrolling at " + REFLOW_WIDTH + " CSS px width.", Map.of("scrollWidth", res.get("scrollWidth")), url));
                return out;
            }
            String shot = ctx.config().screenshots() ? ctx.artifacts().savePng("reflow-320", driver.screenshot(false)) : null;
            if (offenders == null || offenders.isEmpty()) {
                Finding f = pageFinding(Outcome.FAILED, "Page requires horizontal scrolling at " + REFLOW_WIDTH + " CSS px (scrollWidth " + res.get("scrollWidth") + ").", Map.of("scrollWidth", res.get("scrollWidth")), url);
                out.add(shot == null ? f : f.withEvidence(f.evidence().withScreenshot(shot)));
                return out;
            }
            for (Map<String, Object> o : offenders) {
                String html = String.valueOf(o.get("html"));
                boolean twoD = html.matches("(?is)^<(table|img|video|canvas|svg|pre|iframe|map)\\b.*") || html.contains("role=\"grid\"") || html.contains("class=\"map");
                Finding f = finding(twoD ? Outcome.NEEDS_REVIEW : Outcome.FAILED, String.valueOf(o.get("selector")), html, null,
                        (twoD ? "Content that may require two-dimensional layout extends beyond the " : "Element extends beyond the ")
                                + REFLOW_WIDTH + " px viewport (right edge at " + o.get("right") + " px), causing horizontal scrolling." + (twoD ? " Confirm the exception applies." : ""),
                        Map.of("right", o.get("right"), "width", o.get("width"), "scrollWidth", res.get("scrollWidth")), url);
                out.add(shot == null ? f : f.withEvidence(f.evidence().withScreenshot(shot)));
            }
            return out;
        } finally {
            driver.setViewport(original);
            driver.waitMillis(200);
        }
    }
}
