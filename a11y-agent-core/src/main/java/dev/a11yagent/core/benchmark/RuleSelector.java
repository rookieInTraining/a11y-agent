package dev.a11yagent.core.benchmark;

import dev.a11yagent.core.model.Finding;

/**
 * Points at a rule, optionally narrowed to one of its sub-checks: {@code "aria-validity#role-valid"}
 * selects only the findings that rule emits for the {@code role-valid} check. This is what lets one
 * rule cover several ACT rules while still being scored precisely.
 */
public record RuleSelector(String ruleId, String check) {

    public static RuleSelector parse(String spec) {
        int i = spec.indexOf('#');
        return i < 0 ? new RuleSelector(spec, null) : new RuleSelector(spec.substring(0, i), spec.substring(i + 1));
    }

    public boolean matches(Finding f) {
        if (!f.ruleId().equals(ruleId)) {
            return false;
        }
        if (check == null) {
            return true;
        }
        Object actual = f.evidence().data().get("check");
        return check.equals(actual);
    }

    @Override
    public String toString() {
        return check == null ? ruleId : ruleId + "#" + check;
    }
}
