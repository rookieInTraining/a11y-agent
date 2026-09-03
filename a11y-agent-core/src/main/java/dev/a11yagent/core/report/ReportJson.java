package dev.a11yagent.core.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.a11yagent.core.driver.Rect;
import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.PageAudit;
import dev.a11yagent.core.model.Target;
import dev.a11yagent.core.wcag.Criterion;
import dev.a11yagent.core.wcag.Level;
import dev.a11yagent.core.wcag.Wcag;
import dev.a11yagent.core.wcag.WcagVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable JSON representation of an {@link AuditReport} (written by audits, read back by the VPAT command). */
public final class ReportJson {

    public static final int SCHEMA_VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private ReportJson() {
    }

    public static void write(AuditReport report, Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, toJson(report));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static AuditReport read(Path file) {
        try {
            return fromJson(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String toJson(AuditReport r) {
        return toNode(r).toPrettyString();
    }

    public static ObjectNode toNode(AuditReport r) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("tool", "a11y-agent");
        root.put("name", r.name());
        root.put("startedAt", r.startedAt().toString());
        root.put("finishedAt", r.finishedAt().toString());
        root.put("targetVersion", r.targetVersion().label());
        root.put("targetLevel", r.targetLevel().name());
        ArrayNode rules = root.putArray("rulesRun");
        r.rulesRun().forEach(rules::add);
        ObjectNode summary = root.putObject("summary");
        for (Outcome o : Outcome.values()) {
            summary.put(o.name().toLowerCase(), r.count(o));
        }
        ArrayNode pages = root.putArray("pages");
        for (PageAudit p : r.pages()) {
            ObjectNode pn = pages.addObject();
            pn.put("step", p.step());
            pn.put("url", p.url());
            pn.put("title", p.title());
            pn.put("screenshot", p.screenshot());
            ArrayNode fs = pn.putArray("findings");
            p.findings().forEach(f -> fs.add(finding(f)));
        }
        ArrayNode jf = root.putArray("journeyFindings");
        r.journeyFindings().forEach(f -> jf.add(finding(f)));
        return root;
    }

    private static ObjectNode finding(Finding f) {
        ObjectNode n = JSON.createObjectNode();
        n.put("ruleId", f.ruleId());
        ArrayNode cs = n.putArray("criteria");
        f.criteria().stream().map(Criterion::id).sorted().forEach(cs::add);
        n.put("outcome", f.outcome().name());
        n.put("impact", f.impact().name());
        n.put("message", f.message());
        n.put("step", f.step());
        n.put("url", f.url());
        ObjectNode t = n.putObject("target");
        t.put("selector", f.target().selector());
        t.put("html", f.target().html());
        if (f.target().rect() != null) {
            ObjectNode rect = t.putObject("rect");
            rect.put("x", f.target().rect().x());
            rect.put("y", f.target().rect().y());
            rect.put("width", f.target().rect().width());
            rect.put("height", f.target().rect().height());
        }
        ObjectNode e = n.putObject("evidence");
        e.put("screenshot", f.evidence().screenshot());
        e.put("rationale", f.evidence().rationale());
        e.put("model", f.evidence().model());
        e.put("confidence", f.evidence().confidence());
        e.set("data", JSON.valueToTree(f.evidence().data()));
        return n;
    }

    public static AuditReport fromJson(String json) {
        try {
            JsonNode root = JSON.readTree(json);
            List<PageAudit> pages = new ArrayList<>();
            for (JsonNode pn : root.path("pages")) {
                List<Finding> fs = new ArrayList<>();
                pn.path("findings").forEach(fn -> fs.add(readFinding(fn)));
                pages.add(new PageAudit(pn.path("step").asText(), text(pn, "url"), text(pn, "title"), text(pn, "screenshot"), fs));
            }
            List<Finding> jf = new ArrayList<>();
            root.path("journeyFindings").forEach(fn -> jf.add(readFinding(fn)));
            Set<String> rules = new LinkedHashSet<>();
            root.path("rulesRun").forEach(x -> rules.add(x.asText()));
            return new AuditReport(
                    root.path("name").asText(),
                    Instant.parse(root.path("startedAt").asText()),
                    Instant.parse(root.path("finishedAt").asText()),
                    WcagVersion.parse(root.path("targetVersion").asText("2.2")),
                    Level.valueOf(root.path("targetLevel").asText("AAA")),
                    rules, pages, jf);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Finding readFinding(JsonNode n) {
        Set<Criterion> criteria = new LinkedHashSet<>();
        n.path("criteria").forEach(c -> Wcag.find(c.asText()).ifPresent(criteria::add));
        JsonNode t = n.path("target");
        Rect rect = null;
        if (t.has("rect")) {
            JsonNode r = t.path("rect");
            rect = new Rect(r.path("x").asDouble(), r.path("y").asDouble(), r.path("width").asDouble(), r.path("height").asDouble());
        }
        JsonNode e = n.path("evidence");
        Map<String, Object> data = new LinkedHashMap<>();
        if (e.has("data") && e.path("data").isObject()) {
            data = JSON.convertValue(e.path("data"), LinkedHashMap.class);
        }
        return new Finding(
                n.path("ruleId").asText(),
                criteria,
                Outcome.valueOf(n.path("outcome").asText()),
                Impact.valueOf(n.path("impact").asText("MODERATE")),
                n.path("message").asText(),
                new Target(t.path("selector").asText("html"), t.path("html").asText(""), rect),
                new Evidence(text(e, "screenshot"), text(e, "rationale"), text(e, "model"), e.path("confidence").asDouble(1.0), data),
                text(n, "step"),
                text(n, "url"));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isNull() || v.isMissingNode() ? null : v.asText();
    }
}
