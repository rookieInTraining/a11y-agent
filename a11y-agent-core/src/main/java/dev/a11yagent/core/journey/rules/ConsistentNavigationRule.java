package dev.a11yagent.core.journey.rules;

import dev.a11yagent.core.journey.JourneyRule;
import dev.a11yagent.core.journey.StepSnapshot;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.wcag.Wcag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 3.2.3 Consistent Navigation: navigation regions that repeat across pages must present their items in
 * the same relative order. Regions are matched by aria-label, then by selector; items present in both
 * pages are compared as ordered subsequences.
 */
public final class ConsistentNavigationRule extends JourneyRule {

    public ConsistentNavigationRule() {
        super("consistent-navigation",
                "Repeated navigation regions keep the same relative order of items across the pages of a journey.",
                Set.of(Wcag.get("3.2.3")), Impact.MODERATE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Finding> evaluate(List<StepSnapshot> snapshots) {
        List<StepSnapshot> distinct = Journeys.distinctPages(snapshots);
        if (distinct.size() < 2) {
            return List.of(inapplicable("Fewer than two distinct pages in the journey."));
        }
        // key -> list of (snapshot, items)
        Map<String, List<Map.Entry<StepSnapshot, List<String>>>> byRegion = new LinkedHashMap<>();
        for (StepSnapshot s : distinct) {
            for (Map<String, Object> nav : s.list("navs")) {
                String label = String.valueOf(nav.getOrDefault("label", ""));
                String key = !label.isBlank() ? nav.get("tag") + "[" + label.toLowerCase() + "]" : String.valueOf(nav.get("selector"));
                byRegion.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(Map.entry(s, (List<String>) nav.get("items")));
            }
        }
        List<Finding> out = new ArrayList<>();
        int compared = 0;
        for (var e : byRegion.entrySet()) {
            var occurrences = e.getValue();
            if (occurrences.size() < 2) {
                continue;
            }
            var first = occurrences.get(0);
            for (int i = 1; i < occurrences.size(); i++) {
                var other = occurrences.get(i);
                if (first.getKey().url().equals(other.getKey().url())) {
                    continue;
                }
                compared++;
                List<String> a = first.getValue();
                List<String> b = other.getValue();
                Set<String> common = new LinkedHashSet<>(a);
                common.retainAll(new LinkedHashSet<>(b));
                if (common.size() < 2) {
                    continue;
                }
                List<String> orderA = a.stream().filter(common::contains).distinct().toList();
                List<String> orderB = b.stream().filter(common::contains).distinct().toList();
                if (!orderA.equals(orderB)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("region", e.getKey());
                    data.put("orderOn_" + first.getKey().step(), orderA);
                    data.put("orderOn_" + other.getKey().step(), orderB);
                    out.add(finding(Outcome.FAILED, other.getKey(), e.getKey(),
                            "Navigation region " + e.getKey() + " presents its items in a different order on step \"" + other.getKey().step() + "\" than on step \"" + first.getKey().step() + "\": " + orderB + " vs " + orderA + ".", data));
                }
            }
        }
        if (compared == 0) {
            return List.of(inapplicable("No navigation region repeats across the visited pages."));
        }
        if (out.isEmpty()) {
            out.add(passed("Repeated navigation regions keep the same relative order across " + distinct.size() + " pages.", Map.of("regionsCompared", compared)));
        }
        return out;
    }
}
