package dev.a11yagent.core.model;

import java.util.Map;

/**
 * Supporting evidence for a finding.
 *
 * @param screenshot path (relative to the artifacts directory) of a screenshot, or null
 * @param rationale  free-text explanation; for AI judgements this is the model's reasoning
 * @param model      identifier of the model that produced the judgement, or null for deterministic rules
 * @param confidence 0..1 confidence; 1.0 for deterministic rules
 * @param data       rule-specific structured data (measurements, computed values, ...)
 */
public record Evidence(String screenshot, String rationale, String model, double confidence, Map<String, Object> data) {

    public static Evidence deterministic(String rationale, Map<String, Object> data) {
        return new Evidence(null, rationale, null, 1.0, data == null ? Map.of() : data);
    }

    public static Evidence deterministic(String rationale) {
        return deterministic(rationale, Map.of());
    }

    public Evidence withScreenshot(String path) {
        return new Evidence(path, rationale, model, confidence, data);
    }
}
