package dev.a11yagent.core.rules.ai;

import dev.a11yagent.core.ai.Judge;
import dev.a11yagent.core.ai.Verdict;
import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.rules.Findings;
import dev.a11yagent.core.rules.InPageRule;
import dev.a11yagent.core.rules.RuleContext;
import dev.a11yagent.core.rules.RuleKind;
import dev.a11yagent.core.wcag.Wcag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.1.1 Non-text Content: heuristics run in-page (file names, generic words, redundancy, length) and,
 * when a vision model is configured, each remaining informative image is judged against its alt text
 * and surrounding context.
 */
public final class AltTextQualityRule extends InPageRule {

    public AltTextQualityRule() {
        super("alt-text-quality",
                "Alt text is meaningful for the image and its context (file names, generic words, redundancy, length, and vision-model judgement).",
                Set.of(Wcag.get("1.1.1")), Impact.SERIOUS);
    }

    @Override
    public RuleKind kind() {
        return RuleKind.AI;
    }

    @Override
    protected List<Finding> postProcess(RuleContext ctx, List<Finding> findings) {
        if (ctx.judge().isEmpty()) {
            return findings;
        }
        Judge judge = ctx.judge().get();
        List<Finding> out = new ArrayList<>(findings.size());
        for (Finding f : findings) {
            Map<String, Object> data = f.evidence().data();
            if (f.outcome() != Outcome.PASSED || !Boolean.TRUE.equals(data.get("aiCandidate")) || !judge.hasBudget()) {
                out.add(f);
                continue;
            }
            byte[] crop = Findings.elementCrop(ctx, f.target().selector(), 24);
            if (crop == null) {
                out.add(f);
                continue;
            }
            String question = """
                    Success criterion: WCAG 1.1.1 Non-text Content.
                    Question: Does the alt text provide an equivalent purpose for this image in its context?
                    Judge whether a person who cannot see the image gets the same information/function.
                    FAIL if the alt text is misleading, omits information the image conveys that matters in context,
                    is keyword stuffing, or describes a decorative image that should have alt="".
                    PASS if it is an adequate text alternative (it does not need to be exhaustive).

                    alt="%s"
                    Image size: %sx%s px
                    Inside a link/button: %s (link text: "%s")
                    Caption: "%s"
                    Nearby text: "%s"
                    The attached screenshot shows the image with a little surrounding context.
                    """.formatted(data.get("alt"), data.get("width"), data.get("height"), data.get("inLink"),
                    data.get("linkText"), data.get("caption"), data.get("context"));
            Verdict v = judge.judge(question, List.of(crop));
            String shot = ctx.artifacts().savePng("alt-text-ai", crop);
            Map<String, Object> newData = new HashMap<>(data);
            newData.put("aiVerdict", v.result().name());
            Evidence ev = new Evidence(shot, v.rationale(), v.model(), v.confidence(), newData);
            Finding judged = switch (v.result()) {
                case FAIL -> f.withOutcome(v.confidence() >= 0.75 ? Outcome.FAILED : Outcome.NEEDS_REVIEW,
                        "Vision model judged the alt text \"" + data.get("alt") + "\" inadequate: " + v.rationale());
                case PASS -> f.withOutcome(Outcome.PASSED, "Vision model judged the alt text adequate: " + v.rationale());
                case UNSURE -> f.withOutcome(Outcome.CANT_TELL, "Vision model could not judge the alt text: " + v.rationale());
            };
            out.add(judged.withEvidence(ev));
        }
        return out;
    }
}
