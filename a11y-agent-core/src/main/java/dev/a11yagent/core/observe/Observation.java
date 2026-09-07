package dev.a11yagent.core.observe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Log of DOM/focus/live-region changes collected while a user action runs. */
public record Observation(
        String urlBefore,
        String urlAfter,
        String primarySelector,
        List<Map<String, Object>> events) {

    public Observation {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static Observation empty(String url) {
        return new Observation(url, url, null, List.of());
    }

    @SuppressWarnings("unchecked")
    public static Observation fromMap(Map<String, Object> raw) {
        if (raw == null) {
            return empty("");
        }
        List<Map<String, Object>> events = new ArrayList<>();
        Object ev = raw.get("events");
        if (ev instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    events.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
        }
        return new Observation(
                str(raw.get("urlBefore")),
                str(raw.get("urlAfter")),
                strOrNull(raw.get("primarySelector")),
                events);
    }

    public boolean hasEvents() {
        return !events.isEmpty();
    }

    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("urlBefore", urlBefore);
        data.put("urlAfter", urlAfter);
        if (primarySelector != null) {
            data.put("primarySelector", primarySelector);
        }
        data.put("eventCount", events.size());
        data.put("events", events);
        return data;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String strOrNull(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o);
        return s.isEmpty() ? null : s;
    }
}
