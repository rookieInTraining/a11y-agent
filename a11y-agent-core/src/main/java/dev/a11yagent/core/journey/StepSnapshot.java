package dev.a11yagent.core.journey;

import java.util.List;
import java.util.Map;

/**
 * Structural snapshot of one page state, produced by the in-page bundle's {@code snapshot()} and
 * consumed by cross-step rules.
 */
public record StepSnapshot(int index, String step, String url, String title, Map<String, Object> data) {

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> list(String key) {
        Object v = data.get(key);
        return v instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    public boolean flag(String key) {
        return Boolean.TRUE.equals(data.get(key));
    }

    public int number(String key) {
        return data.get(key) instanceof Number n ? n.intValue() : 0;
    }
}
