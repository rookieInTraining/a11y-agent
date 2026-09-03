package dev.a11yagent.core.rules;

import dev.a11yagent.core.driver.Rect;
import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.Target;
import dev.a11yagent.core.wcag.Criterion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Helpers to turn raw in-page results into {@link Finding}s and to attach evidence. */
public final class Findings {

    private static final int MAX_SCREENSHOTS_PER_RULE = 20;

    private Findings() {
    }

    public static Outcome outcome(Object raw) {
        String s = String.valueOf(raw);
        return switch (s) {
            case "passed" -> Outcome.PASSED;
            case "failed" -> Outcome.FAILED;
            case "inapplicable" -> Outcome.INAPPLICABLE;
            case "needsReview" -> Outcome.NEEDS_REVIEW;
            default -> Outcome.CANT_TELL;
        };
    }

    @SuppressWarnings("unchecked")
    public static Finding fromRaw(String ruleId, Set<Criterion> criteria, Impact impact, Map<String, Object> raw, String url) {
        Outcome outcome = outcome(raw.get("outcome"));
        Target target = new Target(
                String.valueOf(raw.getOrDefault("selector", "html")),
                String.valueOf(raw.getOrDefault("html", "")),
                Rect.from(raw.get("rect")));
        Map<String, Object> data = raw.get("data") instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : Map.of();
        String message = String.valueOf(raw.getOrDefault("message", ""));
        return Finding.builder(ruleId)
                .criteria(criteria)
                .outcome(outcome)
                .impact(impact)
                .message(message)
                .target(target)
                .evidence(Evidence.deterministic(message, data))
                .url(url)
                .build();
    }

    public static List<Finding> fromRaw(String ruleId, Set<Criterion> criteria, Impact impact, List<Map<String, Object>> raws, String url) {
        List<Finding> out = new ArrayList<>(raws.size());
        for (Map<String, Object> raw : raws) {
            out.add(fromRaw(ruleId, criteria, impact, raw, url));
        }
        return out;
    }

    /** Captures element screenshots for issues (bounded per rule so large pages stay fast). */
    public static List<Finding> attachScreenshots(RuleContext ctx, List<Finding> findings) {
        if (!ctx.config().screenshots()) {
            return findings;
        }
        List<Finding> out = new ArrayList<>(findings.size());
        int taken = 0;
        for (Finding f : findings) {
            if (f.outcome().isIssue() && taken < MAX_SCREENSHOTS_PER_RULE && f.target().rect() != null && !f.target().rect().isEmpty()) {
                String path = elementScreenshot(ctx, f.target().selector(), f.ruleId(), 16);
                if (path != null) {
                    out.add(f.withEvidence(f.evidence().withScreenshot(path)));
                    taken++;
                    continue;
                }
            }
            out.add(f);
        }
        return out;
    }

    /** Screenshot of an element with padding, after scrolling it into view. Returns the artifact path or null. */
    public static String elementScreenshot(RuleContext ctx, String selector, String prefix, int pad) {
        byte[] png = elementCrop(ctx, selector, pad);
        return png == null ? null : ctx.artifacts().savePng(prefix, png);
    }

    public static byte[] elementCrop(RuleContext ctx, String selector, int pad) {
        try {
            Object clip = ctx.inPage().call("elementCrop", Map.of("selector", selector, "pad", pad));
            Rect r = Rect.from(clip);
            if (r == null || r.isEmpty()) {
                return null;
            }
            return ctx.driver().screenshotClip(r);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Finding inapplicable(String ruleId, Set<Criterion> criteria, String message, String url) {
        return Finding.builder(ruleId).criteria(criteria).outcome(Outcome.INAPPLICABLE).impact(Impact.MINOR)
                .message(message).evidence(Evidence.deterministic(message)).url(url).build();
    }
}
