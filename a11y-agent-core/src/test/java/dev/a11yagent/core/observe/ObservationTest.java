package dev.a11yagent.core.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObservationTest {

    @Test
    void fromMapParsesEvents() {
        Observation obs = Observation.fromMap(Map.of(
                "urlBefore", "https://example.com/form",
                "urlAfter", "https://example.com/success",
                "events", List.of(Map.of("kind", "focus", "selector", "#submit"))));
        assertEquals("https://example.com/form", obs.urlBefore());
        assertEquals("https://example.com/success", obs.urlAfter());
        assertEquals(1, obs.events().size());
        assertTrue(obs.hasEvents());
    }

    @Test
    void toDataRoundTripsSummary() {
        Observation obs = new Observation("a", "b", "#x", List.of(Map.of("kind", "error-node")));
        Map<String, Object> data = obs.toData();
        assertEquals(1, data.get("eventCount"));
        assertEquals("a", data.get("urlBefore"));
    }
}
