package dev.a11yagent.core.journey.rules;

import dev.a11yagent.core.journey.StepSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Journeys {

    private Journeys() {
    }

    /** First snapshot for each distinct URL (ignoring fragments). */
    static List<StepSnapshot> distinctPages(List<StepSnapshot> snapshots) {
        Set<String> seen = new HashSet<>();
        List<StepSnapshot> out = new ArrayList<>();
        for (StepSnapshot s : snapshots) {
            String key = stripFragment(s.url());
            if (seen.add(key)) {
                out.add(s);
            }
        }
        return out;
    }

    static String stripFragment(String url) {
        if (url == null) {
            return "";
        }
        int i = url.indexOf('#');
        return i < 0 ? url : url.substring(0, i);
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
