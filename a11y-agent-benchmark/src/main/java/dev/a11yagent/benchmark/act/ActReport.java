package dev.a11yagent.benchmark.act;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Scores {@link ActResult}s per ACT rule and writes JSON, EARL and a console summary. */
public final class ActReport {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public record RuleScore(String actRuleId, String actRuleName, int cases, int correct, int exact,
                            int falsePositives, int falseNegatives, List<String> ruleIds) {
        public double accuracy() {
            return cases == 0 ? 0 : (double) correct / cases;
        }

        public boolean consistent() {
            return falsePositives == 0 && falseNegatives == 0;
        }
    }

    private final List<ActResult> results;
    private final int totalCorpusCases;
    private final int totalCorpusRules;

    public ActReport(List<ActResult> results, int totalCorpusCases, int totalCorpusRules) {
        this.results = results;
        this.totalCorpusCases = totalCorpusCases;
        this.totalCorpusRules = totalCorpusRules;
    }

    public Map<String, RuleScore> scores() {
        Map<String, List<ActResult>> byRule = new LinkedHashMap<>();
        for (ActResult r : results) {
            byRule.computeIfAbsent(r.testCase().ruleId(), k -> new ArrayList<>()).add(r);
        }
        Map<String, RuleScore> out = new LinkedHashMap<>();
        byRule.forEach((ruleId, list) -> {
            int correct = 0;
            int exact = 0;
            int fp = 0;
            int fn = 0;
            for (ActResult r : list) {
                if (r.correct()) {
                    correct++;
                } else if (r.testCase().expected() == ActTestCase.Expected.FAILED) {
                    fn++;
                } else {
                    fp++;
                }
                if (r.exact()) {
                    exact++;
                }
            }
            out.put(ruleId, new RuleScore(ruleId, list.get(0).testCase().ruleName(), list.size(), correct, exact, fp, fn,
                    List.copyOf(ActMapping.rulesFor(ruleId))));
        });
        return out;
    }

    public int cases() {
        return results.size();
    }

    public long correct() {
        return results.stream().filter(ActResult::correct).count();
    }

    public long exact() {
        return results.stream().filter(ActResult::exact).count();
    }

    public long falsePositives() {
        return results.stream().filter(r -> !r.correct() && r.testCase().expected() != ActTestCase.Expected.FAILED).count();
    }

    public long falseNegatives() {
        return results.stream().filter(r -> !r.correct() && r.testCase().expected() == ActTestCase.Expected.FAILED).count();
    }

    public double accuracy() {
        return cases() == 0 ? 0 : (double) correct() / cases();
    }

    public double exactAccuracy() {
        return cases() == 0 ? 0 : (double) exact() / cases();
    }

    public long consistentRules() {
        return scores().values().stream().filter(RuleScore::consistent).count();
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("W3C ACT Rules benchmark\n");
        sb.append("=======================\n");
        sb.append(String.format("Corpus            : %d test cases across %d ACT rules%n", totalCorpusCases, totalCorpusRules));
        sb.append(String.format("Claimed           : %d ACT rules (%d test cases evaluated)%n", scores().size(), cases()));
        sb.append(String.format("Accuracy          : %.1f%%  (%d/%d correct: violations detected, no false alarms)%n", accuracy() * 100, correct(), cases()));
        sb.append(String.format("Exact-outcome     : %.1f%%  (%d/%d identical outcome, cantTell counted wrong)%n", exactAccuracy() * 100, exact(), cases()));
        sb.append(String.format("False negatives   : %d (missed violations)%n", falseNegatives()));
        sb.append(String.format("False positives   : %d (flagged compliant content)%n", falsePositives()));
        sb.append(String.format("Fully consistent  : %d/%d claimed rules have zero errors%n%n", consistentRules(), scores().size()));

        sb.append(String.format("%-8s %-5s %-5s %-4s %-4s %-7s %s%n", "ACT", "cases", "acc", "FP", "FN", "status", "rule / a11y-agent rules"));
        scores().values().stream()
                .sorted(Comparator.comparingDouble(RuleScore::accuracy).thenComparing(RuleScore::cases, Comparator.reverseOrder()))
                .forEach(s -> sb.append(String.format("%-8s %-5d %-5.0f %-4d %-4d %-7s %s [%s]%n",
                        s.actRuleId(), s.cases(), s.accuracy() * 100, s.falsePositives(), s.falseNegatives(),
                        s.consistent() ? "OK" : "ERRORS", s.actRuleName(), String.join(", ", s.ruleIds()))));
        return sb.toString();
    }

    public String errorDetail(int limit) {
        StringBuilder sb = new StringBuilder("Incorrect results\n-----------------\n");
        int n = 0;
        for (ActResult r : results) {
            if (r.correct()) {
                continue;
            }
            if (n++ >= limit) {
                sb.append("... (").append(results.stream().filter(x -> !x.correct()).count() - limit).append(" more)\n");
                break;
            }
            sb.append(String.format("%-14s %-8s expected %-12s got %-12s %s%n", r.testCase().ruleId(), r.kind(),
                    r.testCase().expected(), r.actual(), r.testCase().url()));
            r.messages().forEach(m -> sb.append("                 ").append(m.length() > 150 ? m.substring(0, 150) + "…" : m).append('\n'));
        }
        return sb.toString();
    }

    public void write(Path dir) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("act-summary.txt"), summary() + "\n" + errorDetail(500));
            Files.writeString(dir.resolve("act-results.json"), toJson());
            Files.writeString(dir.resolve("act-earl.json"), toEarl());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String toJson() {
        ObjectNode root = JSON.createObjectNode();
        root.put("benchmark", "W3C ACT Rules test cases");
        root.put("tool", "a11y-agent");
        root.put("generatedAt", Instant.now().toString());
        root.put("corpusCases", totalCorpusCases);
        root.put("corpusRules", totalCorpusRules);
        root.put("claimedRules", scores().size());
        root.put("evaluatedCases", cases());
        root.put("accuracy", Math.round(accuracy() * 1000) / 10.0);
        root.put("exactAccuracy", Math.round(exactAccuracy() * 1000) / 10.0);
        root.put("falsePositives", falsePositives());
        root.put("falseNegatives", falseNegatives());
        ArrayNode rules = root.putArray("rules");
        scores().values().forEach(s -> {
            ObjectNode n = rules.addObject();
            n.put("actRuleId", s.actRuleId());
            n.put("actRuleName", s.actRuleName());
            n.put("cases", s.cases());
            n.put("correct", s.correct());
            n.put("exact", s.exact());
            n.put("falsePositives", s.falsePositives());
            n.put("falseNegatives", s.falseNegatives());
            n.put("accuracy", Math.round(s.accuracy() * 1000) / 10.0);
            ArrayNode ids = n.putArray("a11yAgentRules");
            s.ruleIds().forEach(ids::add);
        });
        ArrayNode gaps = root.putArray("notImplemented");
        ActMapping.NOT_IMPLEMENTED.forEach((id, reason) -> {
            ObjectNode n = gaps.addObject();
            n.put("actRuleId", id);
            n.put("reason", reason);
        });
        ArrayNode cases = root.putArray("cases");
        results.forEach(r -> {
            ObjectNode n = cases.addObject();
            n.put("actRuleId", r.testCase().ruleId());
            n.put("url", r.testCase().url());
            n.put("expected", r.testCase().expected().name());
            n.put("actual", r.actual().name());
            n.put("kind", r.kind());
            ArrayNode msgs = n.putArray("messages");
            r.messages().forEach(msgs::add);
        });
        return root.toPrettyString();
    }

    /** EARL+JSON-LD assertions, the format the ACT community expects for implementation reports. */
    public String toEarl() {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode ctx = root.putArray("@context");
        ctx.add("http://www.w3.org/ns/earl");
        ObjectNode extra = ctx.addObject();
        extra.put("WCAG2", "http://www.w3.org/TR/WCAG2/#");
        root.put("@type", "Assertion");
        ArrayNode graph = root.putArray("@graph");
        for (ActResult r : results) {
            ObjectNode a = graph.addObject();
            a.put("@type", "Assertion");
            ObjectNode subject = a.putObject("subject");
            subject.put("@type", "TestSubject");
            subject.put("source", r.testCase().url());
            ObjectNode assertedBy = a.putObject("assertedBy");
            assertedBy.put("@type", "Assertor");
            assertedBy.put("title", "a11y-agent");
            ObjectNode test = a.putObject("test");
            test.put("@type", "TestCase");
            test.put("title", r.testCase().ruleName());
            test.put("isPartOf", r.testCase().ruleId());
            ObjectNode result = a.putObject("result");
            result.put("@type", "TestResult");
            result.put("outcome", "earl:" + switch (r.actual()) {
                case PASSED -> "passed";
                case FAILED -> "failed";
                case INAPPLICABLE -> "inapplicable";
                case CANT_TELL, NEEDS_REVIEW -> "cantTell";
            });
            if (!r.messages().isEmpty()) {
                result.put("description", String.join(" | ", r.messages()));
            }
        }
        return root.toPrettyString();
    }
}
