package dev.a11yagent.core.rules;

import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.wcag.Criterion;
import java.util.List;
import java.util.Set;

/** A page-level accessibility rule. */
public interface Rule {

    /** Stable kebab-case identifier, e.g. {@code focus-visible}. */
    String id();

    /** Short human readable description of what the rule checks. */
    String description();

    Set<Criterion> criteria();

    RuleKind kind();

    /**
     * Evaluates the rule against the current page state. Implementations should return a single
     * INAPPLICABLE finding when nothing on the page is a target, and one PASSED/FAILED/NEEDS_REVIEW
     * finding per target otherwise.
     */
    List<Finding> evaluate(RuleContext ctx);
}
