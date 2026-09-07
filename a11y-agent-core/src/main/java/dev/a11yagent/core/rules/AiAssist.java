package dev.a11yagent.core.rules;

/** How a rule uses a vision/language model, if at all. */
public enum AiAssist {
    /** Fully deterministic: never calls a model. */
    NONE,
    /** Deterministic first; the model only judges ambiguous leftovers. */
    ENHANCED,
    /** The model is the check; heuristics only pre-filter or run when no model is configured. */
    AI_ONLY;

    public String label() {
        return switch (this) {
            case NONE -> "Deterministic";
            case ENHANCED -> "AI-enhanced";
            case AI_ONLY -> "AI-only";
        };
    }
}
