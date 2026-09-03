package dev.a11yagent.core.rules.runtime;

import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.rules.RuleContext;
import dev.a11yagent.core.wcag.Wcag;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.4.4 Resize Text: scales text to 200% (CSS zoom on the root element, an approximation of browser
 * zoom) and reports text that becomes clipped or overlapping. Horizontal overflow at 200% is only
 * flagged for review because 1.4.4 permits scrolling as long as content stays readable.
 */
public final class ResizeTextRule extends RuntimeRule {

    public ResizeTextRule() {
        super("resize-text",
                "Text can be scaled to 200% without loss of content or functionality.",
                Set.of(Wcag.get("1.4.4")), Impact.SERIOUS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Finding> evaluate(RuleContext ctx) {
        var inPage = ctx.inPage();
        var driver = ctx.driver();
        String url = driver.url();
        List<Map<String, Object>> before = (List<Map<String, Object>>) inPage.call("textClipping", null);
        Set<String> baseline = new HashSet<>();
        before.forEach(m -> baseline.add(String.valueOf(m.get("selector"))));
        List<Finding> out = new ArrayList<>();
        try {
            driver.evaluate("() => { document.documentElement.style.setProperty('zoom', '2'); }");
            driver.waitMillis(300);
            List<Map<String, Object>> after = (List<Map<String, Object>>) inPage.call("textClipping", null);
            for (Map<String, Object> m : after) {
                String sel = String.valueOf(m.get("selector"));
                if (baseline.contains(sel)) {
                    continue;
                }
                out.add(finding(Outcome.FAILED, sel, String.valueOf(m.get("html")), null,
                        "Text is " + m.get("reason") + " at 200% text size; content is lost.", m, url));
            }
            Map<String, Object> overflow = (Map<String, Object>) inPage.call("horizontalOverflow", null);
            if (Boolean.TRUE.equals(overflow.get("overflow"))) {
                out.add(pageFinding(Outcome.NEEDS_REVIEW, "Page scrolls horizontally at 200% zoom (scrollWidth " + overflow.get("scrollWidth") + "). Confirm all text remains readable without excessive 2D scrolling.", Map.of("scrollWidth", overflow.get("scrollWidth")), url));
            }
        } finally {
            driver.evaluate("() => { document.documentElement.style.removeProperty('zoom'); }");
            driver.waitMillis(200);
        }
        if (out.isEmpty()) {
            out.add(pageFinding(Outcome.PASSED, "No clipping or overlap introduced at 200% text size.", Map.of(), url));
        }
        return out;
    }
}
