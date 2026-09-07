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
        KeyboardTraversal.Result t = KeyboardTraversal.ofForTrapProbe(ctx);
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
            boolean keyHandler = t.stops().stream().anyMatch(s -> {
                String h = s.html() == null ? "" : s.html().toLowerCase();
                return h.contains("onkeydown") || h.contains("onkeyup") || h.contains("onkeypress");
            }) || hasPageKeyHandler(ctx);
            String pageText = pageInnerText(ctx).toLowerCase();
            boolean documented = pageText.contains("to exit") || pageText.contains("to leave")
                    || pageText.contains("how to go") || pageText.contains("press ctrl")
                    || pageText.contains("press the")
                    || pageText.matches("(?s).*press\\s+.+\\b(exit|leave|close|escape).*");
            Map<String, Object> data = Map.of("presses", t.presses(), "distinctVisited", t.distinctVisited(), "tabbables", t.tabbables().size());
            // ACT allows a non-Tab exit when it is both implemented and documented. We did not press
            // that key, so both together is cantTell (accepted for expected passed); one without the
            // other is still a trap.
            if (keyHandler && documented) {
                return List.of(pageFinding(Outcome.CANT_TELL,
                        "Tab cycles between " + t.distinctVisited() + " of the " + t.tabbables().size()
                                + " focusable elements, but a documented non-Tab key handler may release the cycle.",
                        data, url));
            }
            KeyboardTraversal.Stop stop = t.stops().stream().filter(s -> s.selector().equals(t.revisited())).findFirst().orElse(null);
            String msg = "Tab cycles between " + t.distinctVisited() + " of the " + t.tabbables().size()
                    + " focusable elements, returning repeatedly to " + t.revisited()
                    + " instead of continuing through the page. Focus is trapped in that group; only a non-standard key would release it.";
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

    private static boolean hasPageKeyHandler(RuleContext ctx) {
        try {
            Object v = ctx.driver().evaluate("() => !!document.querySelector('[onkeydown],[onkeyup],[onkeypress]')");
            return Boolean.TRUE.equals(v);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String pageInnerText(RuleContext ctx) {
        try {
            Object text = ctx.driver().evaluate("() => (document.body && document.body.innerText) || ''");
            return text == null ? "" : String.valueOf(text);
        } catch (RuntimeException e) {
            return "";
        }
    }
}
