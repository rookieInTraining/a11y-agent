package dev.a11yagent.core.journey.rules;

import dev.a11yagent.core.journey.JourneyRule;
import dev.a11yagent.core.journey.StepSnapshot;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.wcag.Wcag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 3.2.4 Consistent Identification: components with the same function (same link destination, same icon)
 * must be identified consistently (same accessible name) across pages.
 */
public final class ConsistentIdentificationRule extends JourneyRule {

    public ConsistentIdentificationRule() {
        super("consistent-identification",
                "Links to the same destination and controls using the same icon have the same accessible name on every page.",
                Set.of(Wcag.get("3.2.4")), Impact.MODERATE);
    }

    @Override
    public List<Finding> evaluate(List<StepSnapshot> snapshots) {
        List<StepSnapshot> pages = Journeys.distinctPages(snapshots);
        if (pages.size() < 2) {
            return List.of(inapplicable("Fewer than two distinct pages in the journey."));
        }
        // href -> name -> first snapshot/selector where seen
        Map<String, Map<String, Object[]>> byHref = new LinkedHashMap<>();
        Map<String, Map<String, Object[]>> byIcon = new LinkedHashMap<>();
        for (StepSnapshot s : pages) {
            for (Map<String, Object> l : s.list("links")) {
                String href = Journeys.str(l.get("href"));
                String name = Journeys.str(l.get("name")).toLowerCase();
                if (href.isEmpty() || name.isEmpty() || href.startsWith("javascript:") || href.equals(Journeys.stripFragment(s.url()))) {
                    continue;
                }
                byHref.computeIfAbsent(href, k -> new LinkedHashMap<>()).putIfAbsent(name, new Object[] {s, l.get("selector")});
            }
            for (Map<String, Object> ic : s.list("icons")) {
                String src = Journeys.str(ic.get("src"));
                String name = Journeys.str(ic.get("name")).toLowerCase();
                if (src.isEmpty() || src.equals("svg:")) {
                    continue;
                }
                byIcon.computeIfAbsent(src, k -> new LinkedHashMap<>()).putIfAbsent(name, new Object[] {s, ic.get("selector")});
            }
        }
        List<Finding> out = new ArrayList<>();
        report(byHref, "Links to \"%s\" are labelled differently across pages: %s.", out);
        report(byIcon, "Controls using icon \"%s\" are labelled differently across pages: %s.", out);
        if (out.isEmpty()) {
            out.add(passed("Repeated links and icons are identified consistently across " + pages.size() + " pages.", Map.of("destinations", byHref.size(), "icons", byIcon.size())));
        }
        return out;
    }

    private void report(Map<String, Map<String, Object[]>> index, String template, List<Finding> out) {
        for (var e : index.entrySet()) {
            Map<String, Object[]> names = e.getValue();
            if (names.size() < 2) {
                continue;
            }
            // Pages where the name set spans several pages (same page using two names is 2.4.4 territory, not 3.2.4).
            Set<String> pagesInvolved = new LinkedHashSet<>();
            names.values().forEach(v -> pagesInvolved.add(((StepSnapshot) v[0]).url()));
            if (pagesInvolved.size() < 2) {
                continue;
            }
            Object[] last = names.values().stream().reduce((a, b) -> b).orElseThrow();
            out.add(finding(Outcome.FAILED, (StepSnapshot) last[0], Journeys.str(last[1]),
                    String.format(template, e.getKey(), names.keySet()),
                    Map.of("key", e.getKey(), "names", new ArrayList<>(names.keySet()), "pages", new ArrayList<>(pagesInvolved))));
        }
    }
}
