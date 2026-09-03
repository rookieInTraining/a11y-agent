package dev.a11yagent.core.journey.rules;

import dev.a11yagent.core.journey.JourneyRule;
import dev.a11yagent.core.journey.StepSnapshot;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.wcag.Wcag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 3.2.6 Consistent Help: help mechanisms (contact details, help links, chat, FAQ) that repeat across
 * pages must appear in the same relative place. The rule compares the landmark region and the ordinal
 * position among that region's links.
 */
public final class ConsistentHelpRule extends JourneyRule {

    public ConsistentHelpRule() {
        super("consistent-help",
                "Help mechanisms that repeat across pages appear in the same relative order and region.",
                Set.of(Wcag.get("3.2.6")), Impact.MODERATE);
    }

    private record Occurrence(StepSnapshot snap, String region, int rankInRegion, String selector) {
    }

    @Override
    public List<Finding> evaluate(List<StepSnapshot> snapshots) {
        List<StepSnapshot> pages = Journeys.distinctPages(snapshots);
        if (pages.size() < 2) {
            return List.of(inapplicable("Fewer than two distinct pages in the journey."));
        }
        Map<String, List<Occurrence>> byMechanism = new LinkedHashMap<>();
        for (StepSnapshot s : pages) {
            List<Map<String, Object>> links = s.list("links");
            for (Map<String, Object> h : s.list("help")) {
                String href = Journeys.str(h.get("href"));
                String name = Journeys.str(h.get("name")).toLowerCase();
                String key = !href.isEmpty() ? "href:" + href : "name:" + name;
                String region = Journeys.str(h.get("region"));
                int order = h.get("order") instanceof Number n ? n.intValue() : -1;
                int rank = 0;
                if (order >= 0) {
                    for (Map<String, Object> l : links) {
                        int lo = l.get("order") instanceof Number n ? n.intValue() : -1;
                        if (region.equals(Journeys.str(l.get("region"))) && lo >= 0 && lo < order) {
                            rank++;
                        }
                    }
                }
                byMechanism.computeIfAbsent(key, k -> new ArrayList<>()).add(new Occurrence(s, region, rank, Journeys.str(h.get("selector"))));
            }
        }
        List<Finding> out = new ArrayList<>();
        int repeated = 0;
        for (var e : byMechanism.entrySet()) {
            List<Occurrence> occ = e.getValue();
            if (occ.size() < 2) {
                continue;
            }
            repeated++;
            Occurrence first = occ.get(0);
            for (int i = 1; i < occ.size(); i++) {
                Occurrence o = occ.get(i);
                Map<String, Object> data = Map.of("mechanism", e.getKey(),
                        "firstRegion", first.region(), "firstRank", first.rankInRegion(), "firstStep", first.snap().step(),
                        "region", o.region(), "rank", o.rankInRegion());
                if (!first.region().equals(o.region())) {
                    out.add(finding(Outcome.FAILED, o.snap(), o.selector(),
                            "Help mechanism " + e.getKey() + " is in the " + first.region() + " region on step \"" + first.snap().step() + "\" but in the " + o.region() + " region on step \"" + o.snap().step() + "\".", data));
                } else if (first.rankInRegion() != o.rankInRegion()) {
                    out.add(finding(Outcome.NEEDS_REVIEW, o.snap(), o.selector(),
                            "Help mechanism " + e.getKey() + " changes position within the " + o.region() + " region (link #" + first.rankInRegion() + " on \"" + first.snap().step() + "\" vs #" + o.rankInRegion() + " on \"" + o.snap().step() + "\"). Confirm the relative order to surrounding content is unchanged.", data));
                }
            }
        }
        if (repeated == 0) {
            return List.of(inapplicable("No help mechanism repeats across the visited pages."));
        }
        if (out.isEmpty()) {
            out.add(passed("Repeated help mechanisms keep the same relative position across " + pages.size() + " pages.", Map.of("mechanisms", repeated)));
        }
        return out;
    }
}
