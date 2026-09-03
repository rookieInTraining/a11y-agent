package dev.a11yagent.core.rules.runtime;

import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.rules.Findings;
import dev.a11yagent.core.rules.RuleContext;
import dev.a11yagent.core.wcag.Wcag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2.4.3 Focus Order: compares the real Tab sequence with the visual reading order (rows top-to-bottom,
 * left-to-right) and flags positive tabindex values and elements focused while off-screen.
 */
public final class FocusOrderRule extends RuntimeRule {

    private static final double ROW_TOLERANCE = 12;

    public FocusOrderRule() {
        super("focus-order",
                "Keyboard focus order follows a meaningful (visual reading) sequence; no positive tabindex; no focus on off-screen content.",
                Set.of(Wcag.get("2.4.3")), Impact.MODERATE);
    }

    @Override
    public List<Finding> evaluate(RuleContext ctx) {
        KeyboardTraversal.Result t = KeyboardTraversal.of(ctx);
        String url = ctx.driver().url();
        List<KeyboardTraversal.Stop> stops = t.stops();
        if (stops.size() < 2) {
            return List.of(Findings.inapplicable(id(), criteria(), "Fewer than two keyboard focus stops.", url));
        }
        List<Finding> out = new ArrayList<>();

        for (KeyboardTraversal.Stop s : stops) {
            if (s.tabindex() != null) {
                try {
                    if (Integer.parseInt(s.tabindex().trim()) > 0) {
                        out.add(stopFinding(Outcome.NEEDS_REVIEW, s, "tabindex=\"" + s.tabindex() + "\" overrides the natural focus order; positive tabindex values are fragile and usually produce an illogical sequence.", Map.of("tabindex", s.tabindex()), url));
                    }
                } catch (NumberFormatException ignored) {
                    // not a number, ignore
                }
            }
            if (!s.inViewport() && s.rect() != null && !s.rect().isEmpty()) {
                out.add(stopFinding(Outcome.FAILED, s, "Element received keyboard focus while positioned outside the viewport (likely visually hidden content that is still tabbable).", Map.of("rect", s.rect().toString()), url));
            }
        }

        // Visual reading order: sort by row (y within tolerance), then x.
        List<KeyboardTraversal.Stop> visual = new ArrayList<>(stops.stream().filter(s -> s.rect() != null && !s.rect().isEmpty()).toList());
        visual.sort(Comparator.comparingDouble((KeyboardTraversal.Stop s) -> Math.round(s.rect().y() / ROW_TOLERANCE)).thenComparingDouble(s -> s.rect().x()));
        Map<String, Integer> visualIndex = new HashMap<>();
        for (int i = 0; i < visual.size(); i++) {
            visualIndex.put(visual.get(i).selector(), i);
        }
        int jumps = 0;
        int reported = 0;
        for (int i = 1; i < stops.size(); i++) {
            KeyboardTraversal.Stop prev = stops.get(i - 1);
            KeyboardTraversal.Stop cur = stops.get(i);
            Integer pv = visualIndex.get(prev.selector());
            Integer cv = visualIndex.get(cur.selector());
            if (pv == null || cv == null) {
                continue;
            }
            // Going backwards in reading order by more than a couple of positions is a jump.
            if (cv < pv - 2) {
                jumps++;
                if (reported < 10) {
                    reported++;
                    out.add(stopFinding(Outcome.NEEDS_REVIEW, cur,
                            "Focus moves backwards in the visual reading order: from \"" + prev.name() + "\" (" + prev.selector() + ") to \"" + cur.name() + "\". Verify the sequence preserves meaning and operability.",
                            Map.of("fromSelector", prev.selector(), "fromVisualIndex", pv, "toVisualIndex", cv, "tabIndexPosition", i), url));
                }
            }
        }
        if (out.isEmpty()) {
            out.add(pageFinding(Outcome.PASSED, "Tab order of " + stops.size() + " stops follows the visual reading order.", Map.of("stops", stops.size()), url));
        } else if (jumps == 0) {
            out.add(pageFinding(Outcome.PASSED, "Tab order follows the visual reading order (other focus-order issues reported separately).", Map.of("stops", stops.size()), url));
        }
        return out;
    }
}
