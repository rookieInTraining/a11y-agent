package dev.a11yagent.core.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Which a11y-agent rules implement which ACT rules. */
public final class ActMapping {

    /** @param claimed declared as a fully automated implementation, and therefore part of the headline score */
    public record Entry(String actRuleId, String name, boolean claimed, List<RuleSelector> selectors) {
        public Set<String> ruleIds() {
            java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
            selectors.forEach(s -> ids.add(s.ruleId()));
            return ids;
        }
    }

    private static final String RESOURCE = "/dev/a11yagent/benchmark/act-mapping.json";

    private final Map<String, Entry> byActRule;

    private ActMapping(Map<String, Entry> byActRule) {
        this.byActRule = byActRule;
    }

    public static ActMapping defaults() {
        try (InputStream in = ActMapping.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            return parse(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ActMapping load(Path file) {
        try {
            return parse(new ObjectMapper().readTree(Files.readString(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ActMapping parse(JsonNode root) {
        Map<String, Entry> map = new LinkedHashMap<>();
        root.properties().forEach(e -> {
            String id = e.getKey();
            JsonNode n = e.getValue();
            if (id.startsWith("_") || !n.isObject()) {
                return;
            }
            List<RuleSelector> selectors = new ArrayList<>();
            n.path("selectors").forEach(s -> selectors.add(RuleSelector.parse(s.asText())));
            map.put(id, new Entry(id, n.path("name").asText(id), n.path("claimed").asBoolean(false), List.copyOf(selectors)));
        });
        return new ActMapping(map);
    }

    public Optional<Entry> forActRule(String actRuleId) {
        return Optional.ofNullable(byActRule.get(actRuleId));
    }

    public Map<String, Entry> entries() {
        return Map.copyOf(byActRule);
    }

    public long claimedCount() {
        return byActRule.values().stream().filter(Entry::claimed).count();
    }
}
