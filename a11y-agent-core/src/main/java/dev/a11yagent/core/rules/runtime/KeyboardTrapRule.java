package dev.a11yagent.core.rules.runtime;

import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.rules.Findings;
import dev.a11yagent.core.rules.RuleContext;
import dev.a11yagent.core.wcag.Wcag;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 2.1.2 No Keyboard Trap: detects focus that cannot leave an element with repeated Tab presses. */
public final class KeyboardTrapRule extends RuntimeRule {

    public KeyboardTrapRule() {
        super("no-keyboard-trap",
                "Keyboard focus can be moved away from every component using Tab.",
                Set.of(Wcag.get("2.1.2")), Impact.CRITICAL);
    }

    @Override
    public List<Finding> evaluate(RuleContext ctx) {
        KeyboardTraversal.Result t = KeyboardTraversal.of(ctx);
        String url = ctx.driver().url();
        if (t.stops().isEmpty() && !t.trapped()) {
            return List.of(Findings.inapplicable(id(), criteria(), "No keyboard focusable elements.", url));
        }
        if (t.trapped()) {
            KeyboardTraversal.Stop stop = t.stops().stream().filter(s -> s.selector().equals(t.trapSelector())).findFirst().orElse(null);
            String msg = "Keyboard focus stayed on " + t.trapSelector() + " after repeated Tab presses. Users cannot move focus away with standard keys.";
            Finding f = stop == null
                    ? finding(Outcome.FAILED, t.trapSelector(), "", null, msg, Map.of("presses", t.presses()), url)
                    : stopFinding(Outcome.FAILED, stop, msg, Map.of("presses", t.presses()), url);
            return List.of(f);
        }
        // focus that keeps returning to the same element is cycling inside a subset of the page
        if (t.revisited() != null && t.distinctVisited() < t.tabbables().size()) {
            KeyboardTraversal.Stop stop = t.stops().stream().filter(s -> s.selector().equals(t.revisited())).findFirst().orElse(null);
            String msg = "Tab cycles between " + t.distinctVisited() + " of the " + t.tabbables().size()
                    + " focusable elements, returning repeatedly to " + t.revisited()
                    + " instead of continuing through the page. Focus is trapped in that group; only a non-standard key would release it.";
            Map<String, Object> data = Map.of("presses", t.presses(), "distinctVisited", t.distinctVisited(), "tabbables", t.tabbables().size());
            return List.of(stop == null
                    ? finding(Outcome.FAILED, t.revisited(), "", null, msg, data, url)
                    : stopFinding(Outcome.FAILED, stop, msg, data, url));
        }
        if (t.truncated()) {
            return List.of(pageFinding(Outcome.CANT_TELL,
                    "Tab traversal stopped after " + t.stops().size() + " stops without returning to the start, so it could not be confirmed that focus can leave every component.",
                    Map.of("stops", t.stops().size()), url));
        }
        return List.of(pageFinding(Outcome.PASSED,
                "Focus moved through " + t.stops().size() + " stops" + (t.cycled() ? " and returned to the start" : "") + " without getting trapped.",
                Map.of("stops", t.stops().size(), "cycled", t.cycled()), url));
    }
}
