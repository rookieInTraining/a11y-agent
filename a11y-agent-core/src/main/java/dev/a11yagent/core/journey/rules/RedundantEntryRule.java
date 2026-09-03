package dev.a11yagent.core.journey.rules;

import dev.a11yagent.core.journey.JourneyRule;
import dev.a11yagent.core.journey.StepSnapshot;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.wcag.Wcag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 3.3.7 Redundant Entry: information the user already entered earlier in the same process must be
 * auto-populated or selectable when asked again. Fields are matched by autocomplete token, falling back
 * to the normalised label. Password/confirmation fields are exempt (security exception).
 */
public final class RedundantEntryRule extends JourneyRule {

    public RedundantEntryRule() {
        super("redundant-entry",
                "Information already entered in an earlier step of the journey is not requested again without being pre-filled or selectable.",
                Set.of(Wcag.get("3.3.7")), Impact.MODERATE);
    }

    @Override
    public List<Finding> evaluate(List<StepSnapshot> snapshots) {
        if (snapshots.size() < 2) {
            return List.of(inapplicable("Journey has a single step."));
        }
        Map<String, StepSnapshot> firstSeen = new HashMap<>();
        Map<String, String> firstLabel = new HashMap<>();
        List<Finding> out = new ArrayList<>();
        int fieldsChecked = 0;
        for (StepSnapshot s : snapshots) {
            Map<String, StepSnapshot> seenThisStep = new HashMap<>();
            for (Map<String, Object> f : s.list("fields")) {
                String type = Journeys.str(f.get("type"));
                String label = Journeys.str(f.get("label"));
                String ac = Journeys.str(f.get("autocomplete"));
                if (type.equals("password") || label.contains("confirm") || label.contains("repeat") || label.contains("re-enter") || label.contains("verify")
                        || ac.contains("password") || ac.equals("one-time-code") || label.isEmpty() && ac.isEmpty()) {
                    continue;
                }
                String key = !ac.isEmpty() && !ac.equals("off") && !ac.equals("on") ? "ac:" + ac : "label:" + label;
                fieldsChecked++;
                StepSnapshot earlier = firstSeen.get(key);
                if (earlier != null && !earlier.url().equals(s.url()) && !Boolean.TRUE.equals(f.get("prefilled"))) {
                    out.add(finding(Outcome.NEEDS_REVIEW, s, Journeys.str(f.get("selector")),
                            "Field \"" + label + "\" (" + key + ") was already requested in step \"" + earlier.step() + "\" and is empty again in step \"" + s.step() + "\". It must be auto-populated or selectable unless re-entry is essential or required for security.",
                            Map.of("key", key, "firstStep", earlier.step(), "firstLabel", firstLabel.getOrDefault(key, label))));
                }
                seenThisStep.putIfAbsent(key, s);
            }
            seenThisStep.forEach((k, v) -> {
                firstSeen.putIfAbsent(k, v);
            });
            for (Map<String, Object> f : s.list("fields")) {
                String ac = Journeys.str(f.get("autocomplete"));
                String label = Journeys.str(f.get("label"));
                String key = !ac.isEmpty() && !ac.equals("off") && !ac.equals("on") ? "ac:" + ac : "label:" + label;
                firstLabel.putIfAbsent(key, label);
            }
        }
        if (fieldsChecked == 0) {
            return List.of(inapplicable("No form fields in the journey."));
        }
        if (out.isEmpty()) {
            out.add(passed("No previously entered information is requested again across the journey.", Map.of("fieldsChecked", fieldsChecked)));
        }
        return out;
    }
}
