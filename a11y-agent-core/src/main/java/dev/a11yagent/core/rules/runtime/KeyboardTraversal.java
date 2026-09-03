package dev.a11yagent.core.rules.runtime;

import dev.a11yagent.core.driver.Rect;
import dev.a11yagent.core.rules.RuleContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives the page with real Tab key presses and records every focus stop with its focused styles, the
 * baseline (unfocused) styles of the same element, and whether it is obscured by other content. The
 * traversal is expensive, so it is computed once per page state and shared by the focus rules through
 * {@link RuleContext#cached(String, java.util.function.Supplier)}.
 */
public final class KeyboardTraversal {

    public record Stop(
            int index,
            String selector,
            String tag,
            String name,
            String html,
            Rect rect,
            String tabindex,
            boolean inViewport,
            Map<String, Object> focusedStyles,
            Map<String, Object> baselineStyles,
            Map<String, Object> obscured) {

        /** Style properties that changed between unfocused and focused state. */
        public List<String> changedStyleKeys() {
            List<String> keys = new ArrayList<>();
            if (baselineStyles == null) {
                return keys;
            }
            for (Map.Entry<String, Object> e : focusedStyles.entrySet()) {
                Object base = baselineStyles.get(e.getKey());
                if (base == null && e.getValue() == null) {
                    continue;
                }
                if (base == null || !base.equals(e.getValue())) {
                    keys.add(e.getKey());
                }
            }
            return keys;
        }
    }

    public record Result(List<Stop> stops, List<Map<String, Object>> tabbables, boolean trapped, String trapSelector,
                         boolean cycled, int presses, boolean truncated) {
    }

    private KeyboardTraversal() {
    }

    public static Result of(RuleContext ctx) {
        return ctx.cached("keyboard-traversal", () -> run(ctx));
    }

    @SuppressWarnings("unchecked")
    static Result run(RuleContext ctx) {
        var driver = ctx.driver();
        var inPage = ctx.inPage();
        inPage.ensureInstalled();

        List<Map<String, Object>> tabbables = (List<Map<String, Object>>) inPage.call("tabbables", null);
        Map<String, Map<String, Object>> baseline = new HashMap<>();
        for (Map<String, Object> t : tabbables) {
            baseline.put((String) t.get("selector"), (Map<String, Object>) t.get("styles"));
        }

        inPage.call("blur", null);
        int limit = Math.min(ctx.config().maxFocusStops(), tabbables.size() + 5);
        List<Stop> stops = new ArrayList<>();
        String last = null;
        int repeats = 0;
        boolean trapped = false;
        String trapSelector = null;
        boolean cycled = false;
        boolean left = false;
        int presses = 0;

        for (int i = 0; i < limit + 3; i++) {
            driver.press("Tab");
            presses++;
            Map<String, Object> active = (Map<String, Object>) inPage.call("activeElement", null);
            if (Boolean.TRUE.equals(active.get("body"))) {
                if (left) {
                    cycled = true;
                    break;
                }
                continue;
            }
            left = true;
            String selector = (String) active.get("selector");
            if (selector.equals(last)) {
                repeats++;
                if (repeats >= 2) {
                    trapped = true;
                    trapSelector = selector;
                    break;
                }
                continue;
            }
            repeats = 0;
            last = selector;
            if (!stops.isEmpty() && selector.equals(stops.get(0).selector())) {
                cycled = true;
                break;
            }
            Map<String, Object> obscured = (Map<String, Object>) inPage.call("obscured", null);
            stops.add(new Stop(
                    stops.size(),
                    selector,
                    (String) active.get("tag"),
                    (String) active.get("name"),
                    (String) active.get("html"),
                    Rect.from(active.get("rect")),
                    (String) active.get("tabindex"),
                    Boolean.TRUE.equals(active.get("inViewport")),
                    (Map<String, Object>) active.get("styles"),
                    baseline.get(selector),
                    obscured));
            if (stops.size() >= limit) {
                break;
            }
        }
        inPage.call("blur", null);
        boolean truncated = !cycled && !trapped && stops.size() >= limit;
        return new Result(stops, tabbables, trapped, trapSelector, cycled, presses, truncated);
    }
}
