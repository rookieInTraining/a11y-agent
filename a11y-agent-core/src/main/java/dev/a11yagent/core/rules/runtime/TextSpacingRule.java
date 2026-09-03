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
 * 1.4.12 Text Spacing: injects the WCAG text-spacing overrides (line-height 1.5, paragraph spacing 2em,
 * letter-spacing 0.12em, word-spacing 0.16em) and reports text that becomes clipped, truncated or
 * overlapping compared with the un-modified page.
 */
public final class TextSpacingRule extends RuntimeRule {

    static final String STYLE_ID = "__a11y_agent_text_spacing";
    static final String CSS = """
            * { line-height: 1.5 !important; letter-spacing: 0.12em !important; word-spacing: 0.16em !important; }
            p { margin-bottom: 2em !important; }
            """;

    public TextSpacingRule() {
        super("text-spacing",
                "No loss of content or functionality when line height, paragraph, letter and word spacing are increased to the WCAG 1.4.12 values.",
                Set.of(Wcag.get("1.4.12")), Impact.SERIOUS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Finding> evaluate(RuleContext ctx) {
        var inPage = ctx.inPage();
        String url = ctx.driver().url();
        List<Map<String, Object>> before = (List<Map<String, Object>>) inPage.call("textClipping", null);
        Set<String> baseline = new HashSet<>();
        before.forEach(m -> baseline.add(String.valueOf(m.get("selector"))));
        List<Finding> out = new ArrayList<>();
        try {
            inPage.call("installStyle", Map.of("id", STYLE_ID, "css", CSS));
            ctx.driver().waitMillis(300);
            List<Map<String, Object>> after = (List<Map<String, Object>>) inPage.call("textClipping", null);
            int shots = 0;
            for (Map<String, Object> m : after) {
                String sel = String.valueOf(m.get("selector"));
                if (baseline.contains(sel)) {
                    continue;
                }
                Finding f = finding(Outcome.FAILED, sel, String.valueOf(m.get("html")), null,
                        "Text is " + m.get("reason") + " when WCAG text spacing is applied; content is lost.", m, url);
                if (ctx.config().screenshots() && shots++ < 10) {
                    String shot = dev.a11yagent.core.rules.Findings.elementScreenshot(ctx, sel, id(), 12);
                    if (shot != null) {
                        f = f.withEvidence(f.evidence().withScreenshot(shot));
                    }
                }
                out.add(f);
            }
        } finally {
            inPage.call("removeStyle", STYLE_ID);
        }
        if (out.isEmpty()) {
            out.add(pageFinding(Outcome.PASSED, "No text clipping or overlap introduced by WCAG text spacing.", Map.of("preexistingClipping", before.size()), url));
        }
        return out;
    }
}
