package dev.a11yagent.core.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the ACT test case corpus, either from the W3C {@code testcases.json} or from a local mirror
 * manifest produced alongside a downloaded copy of the pages.
 */
public final class ActCorpus {

    private ActCorpus() {
    }

    public static List<ActCase> load(Path file) {
        try {
            return parse(new ObjectMapper().readTree(Files.readString(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<ActCase> parse(JsonNode root) {
        JsonNode array = root.isArray() ? root : root.path("testcases");
        List<ActCase> out = new ArrayList<>();
        for (JsonNode n : array) {
            String url = n.path("url").asText();
            if (url.isEmpty()) {
                continue;
            }
            List<String> criteria = new ArrayList<>();
            if (n.has("criteria")) {
                n.path("criteria").forEach(c -> criteria.add(c.asText()));
            } else {
                n.path("ruleAccessibilityRequirements").fieldNames().forEachRemaining(k -> {
                    var m = java.util.regex.Pattern.compile("^wcag2\\d:(\\d+\\.\\d+\\.\\d+)$").matcher(k);
                    if (m.matches()) {
                        criteria.add(m.group(1));
                    }
                });
            }
            String path = n.has("path") ? n.path("path").asText() : URI.create(url).getPath();
            out.add(new ActCase(n.path("ruleId").asText(), n.path("ruleName").asText(), n.path("testcaseId").asText(),
                    n.path("expected").asText(), url, path, criteria));
        }
        return out;
    }
}
