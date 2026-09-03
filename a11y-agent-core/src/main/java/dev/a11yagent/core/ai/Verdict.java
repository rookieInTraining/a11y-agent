package dev.a11yagent.core.ai;

/**
 * Structured result of an AI judgement.
 *
 * @param result     PASS, FAIL or UNSURE
 * @param confidence 0..1
 * @param rationale  model explanation, quoted in the report
 * @param model      model identifier
 */
public record Verdict(Result result, double confidence, String rationale, String model) {

    public enum Result { PASS, FAIL, UNSURE }

    public static Verdict unsure(String rationale, String model) {
        return new Verdict(Result.UNSURE, 0.0, rationale, model);
    }
}
