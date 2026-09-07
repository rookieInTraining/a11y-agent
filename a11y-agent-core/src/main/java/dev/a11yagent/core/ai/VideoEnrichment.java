package dev.a11yagent.core.ai;

import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.rules.ArtifactStore;
import dev.a11yagent.core.rules.Rules;
import dev.a11yagent.core.wcag.Criterion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * After probes finish, reviews leftover findings ({@link Outcome#CANT_TELL}, {@link Outcome#NEEDS_REVIEW})
 * against recording frames and the finding text. Never changes a deterministic pass or fail.
 */
public final class VideoEnrichment {

    public static final int MAX_FRAMES = 6;
    public static final String DATA_FLAG = "videoEnrichment";

    private VideoEnrichment() {
    }

    public static boolean isLeftover(Finding f) {
        if (f.evidence().aiJudged()) {
            return false;
        }
        return f.outcome() == Outcome.CANT_TELL || f.outcome() == Outcome.NEEDS_REVIEW;
    }

    public static List<Finding> apply(Judge judge, List<Finding> findings, List<byte[]> frames, ArtifactStore artifacts) {
        if (judge == null || findings.isEmpty()) {
            return findings;
        }
        List<byte[]> images = frames == null ? List.of() : frames.stream().limit(MAX_FRAMES).toList();
        List<Finding> out = new ArrayList<>(findings.size());
        for (Finding f : findings) {
            if (!isLeftover(f) || !judge.hasBudget()) {
                out.add(f);
                continue;
            }
            out.add(review(judge, f, images, artifacts));
        }
        return out;
    }

    private static Finding review(Judge judge, Finding f, List<byte[]> images, ArtifactStore artifacts) {
        Verdict v = judge.judge(prompt(f, images.size()), images);
        Map<String, Object> data = new LinkedHashMap<>(f.evidence().data());
        data.put(DATA_FLAG, true);
        data.put("aiVerdict", v.result().name());
        data.put("priorOutcome", f.outcome().name());
        String shot = f.evidence().screenshot();
        if (shot == null && artifacts != null && !images.isEmpty()) {
            shot = artifacts.savePng("recording-review", images.get(images.size() - 1));
        }
        Evidence ev = new Evidence(shot, v.rationale(), v.model(), v.confidence(), data);
        Outcome next = mapOutcome(f.outcome(), v);
        Impact impact = next == Outcome.FAILED
                ? Rules.pageRule(f.ruleId()).map(r -> r.impact()).orElse(f.impact() == Impact.MINOR ? Impact.SERIOUS : f.impact())
                : f.impact();
        String message = f.message() + " Recording review: " + v.result() + " — " + v.rationale();
        return f.withOutcome(next, message).withImpact(impact).withEvidence(ev);
    }

    static Outcome mapOutcome(Outcome prior, Verdict v) {
        return switch (v.result()) {
            case FAIL -> v.confidence() >= 0.75 ? Outcome.FAILED : Outcome.NEEDS_REVIEW;
            case PASS, UNSURE -> prior;
        };
    }

    static String prompt(Finding f, int frameCount) {
        String criteria = f.criteria().stream()
                .map(c -> c.id() + " " + c.name())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("(none)");
        String ruleHint = ruleSpecificHint(f.ruleId());
        return """
                Post-run review of a leftover automated finding. %s
                This is NOT a full WCAG audit. You may only confirm or reject THIS leftover.
                Rule: %s
                Success criterion: %s
                Current outcome: %s
                Target: %s
                Finding / exception text:
                %s
                %s

                FAIL only if the text and/or frames are evidence that the named criterion is not met.
                An unexpected JavaScript alert or dialog that intercepted Tab or script during a keyboard
                probe is a 2.1.2 keyboard trap (FAIL). A truncated Tab traversal (stop limit reached) is
                not itself a failure; answer UNSURE unless a trap or other failure is visible.
                An agent/driver crash without an accessibility signal (NullPointerException, generic timeout)
                is UNSURE, not FAIL.
                PASS only if the frames clearly show the criterion is met. PASS does not change the stored
                outcome (the leftover stays as-is); still say PASS when that is what you see.
                Otherwise UNSURE.
                """.formatted(
                frameCount == 0
                        ? "No recording frames are attached; judge from the finding text only."
                        : frameCount + " viewport frame(s) from the audit recording are attached (native browser dialogs may be absent from the pixels).",
                f.ruleId(),
                criteria,
                f.outcome(),
                f.target() == null ? "html" : f.target().selector(),
                abbreviate(f.message(), 1800),
                ruleHint);
    }

    private static String ruleSpecificHint(String ruleId) {
        return switch (ruleId) {
            case "error-identification" -> """
                    For 3.3.1 Error Identification: FAIL if visible error text or red error styling appears
                    without aria-invalid, aria-errormessage, aria-describedby, or role=alert association.
                    UNSURE if no form submit or validation action is evidenced.
                    """;
            case "status-messages" -> """
                    For 4.1.3 Status Messages: FAIL if a success/error/status message appeared (including after
                    navigation) without an aria-live region or role=status/alert announcing it. UNSURE if
                    no status change is visible.
                    """;
            default -> "";
        };
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
