package dev.a11yagent.core.rules;

public enum RuleKind {
    /** Evaluated entirely inside the page from DOM/CSSOM/AOM (extension-portable). */
    IN_PAGE,
    /** Needs user-agent behaviour: keyboard events, viewport changes, screenshots. */
    RUNTIME,
    /** Needs a vision or language model. */
    AI,
    /** Evaluated over several page states of a journey. */
    CROSS_STEP
}
