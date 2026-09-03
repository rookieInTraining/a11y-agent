package dev.a11yagent.core.benchmark;

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

/** Aggregates {@link ActResult}s into per-rule and overall figures, and renders them. */
public final class ActSummary {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Counts for one ACT rule (or a whole scope). */
    public static final class Tally {
        public int correct;
        public int falsePositive;
        public int falseNegative;
        public int abstained;

        public int total() {
            return correct + falsePositive + falseNegative + abstained;
        }

        public int wrong() {
            return falsePositive + falseNegative;
        }

        /** Strict accuracy: abstention counts as a miss. */
        public double accuracy() {
            return total() == 0 ? 0 : (double) correct / total();
        }

        /** Consistent in the W3C sense: no wrong answers on any example. */
        public boolean consistent() {
            return wrong() == 0;
        }

        void add(ActVerdict v) {
            switch (v) {
                case CORRECT -> correct++;
                case FALSE_POSITIVE -> falsePositive++;
                case FALSE_NEGATIVE -> falseNegative++;
                case ABSTAINED -> abstained++;
                case OUT_OF_SCOPE -> { }
            }
        }
    }

    private final List<ActResult> results;
    private final Map<String, Tally> byRule = new LinkedHashMap<>();
    private final Map<String, String> ruleNames = new LinkedHashMap<>();
    private final Map<String, Boolean> ruleClaimed = new LinkedHashMap<>();
    private final Tally claimedScope = new Tally();
    private final Tally judgementScope = new Tally();
    private final int outOfScope;
    private final int totalCases;

    public ActSummary(List<ActResult> results, int totalCases) {
        this.results = List.copyOf(results);
        this.totalCases = totalCases;
        int oos = 0;
        for (ActResult r : this.results) {
            if (r.verdict() == ActVerdict.OUT_OF_SCOPE) {
                oos++;
                continue;
            }
            String id = r.testCase().ruleId();
            byRule.computeIfAbsent(id, k -> new Tally()).add(r.verdict());
            ruleNames.putIfAbsent(id, r.testCase().ruleName());
            ruleClaimed.putIfAbsent(id, r.claimed());
            (r.claimed() ? claimedScope : judgementScope).add(r.verdict());
        }
        this.outOfScope = oos;
    }

    public Tally claimedScope() {
        return claimedScope;
    }

    public Tally judgementScope() {
        return judgementScope;
    }

    public Map<String, Tally> byRule() {
        return byRule;
    }

    public List<ActResult> mistakes() {
        return results.stream().filter(r -> r.verdict().isWrong())
                .sorted(Comparator.comparing((ActResult r) -> r.testCase().ruleId())).toList();
    }

    public List<ActResult> abstentions() {
        return results.stream().filter(r -> r.verdict() == ActVerdict.ABSTAINED)
                .sorted(Comparator.comparing((ActResult r) -> r.testCase().ruleId())).toList();
    }

    public String renderConsole() {
        StringBuilder sb = new StringBuilder();
        sb.append("ACT Rules benchmark — a11y-agent\n");
        sb.append("corpus: ").append(totalCases).append(" test cases; out of scope (no mapped rule): ").append(outOfScope).append("\n\n");
        sb.append(String.format("%-8s %-5s %5s %5s %5s %5s %5s  %-7s %s%n", "ACT", "scope", "cases", "ok", "FP", "FN", "abst", "acc", "rule"));
        byRule.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Tally>, Boolean>comparing(e -> !ruleClaimed.get(e.getKey()))
                        .thenComparing(e -> e.getValue().accuracy()))
                .forEach(e -> {
                    Tally t = e.getValue();
                    sb.append(String.format("%-8s %-5s %5d %5d %5d %5d %5d  %6.1f%%  %s%n",
                            e.getKey(), ruleClaimed.get(e.getKey()) ? "claim" : "judge", t.total(), t.correct,
                            t.falsePositive, t.falseNegative, t.abstained, t.accuracy() * 100, ruleNames.get(e.getKey())));
                });
        sb.append('\n');
        sb.append(scopeLine("CLAIMED (fully automated)", claimedScope));
        sb.append(scopeLine("JUDGEMENT (semi-automated)", judgementScope));
        Tally all = new Tally();
        all.correct = claimedScope.correct + judgementScope.correct;
        all.falsePositive = claimedScope.falsePositive + judgementScope.falsePositive;
        all.falseNegative = claimedScope.falseNegative + judgementScope.falseNegative;
        all.abstained = claimedScope.abstained + judgementScope.abstained;
        sb.append(scopeLine("ALL MAPPED", all));
        long consistent = byRule.entrySet().stream().filter(e -> ruleClaimed.get(e.getKey()) && e.getValue().consistent()).count();
        long claimedRules = ruleClaimed.values().stream().filter(b -> b).count();
        sb.append(String.format("%nFully consistent claimed rules (no false positives or negatives): %d/%d%n", consistent, claimedRules));
        return sb.toString();
    }

    private static String scopeLine(String label, Tally t) {
        return String.format("%-28s cases %4d | correct %4d | false pos %3d | false neg %3d | abstained %3d | accuracy %5.1f%%%n",
                label, t.total(), t.correct, t.falsePositive, t.falseNegative, t.abstained, t.accuracy() * 100);
    }

    public void writeJson(Path file) {
        ObjectNode root = JSON.createObjectNode();
        root.put("tool", "a11y-agent");
        root.put("benchmark", "W3C ACT Rules test cases");
        root.put("generatedAt", Instant.now().toString());
        root.put("corpusCases", totalCases);
        root.put("outOfScope", outOfScope);
        putScope(root.putObject("claimedScope"), claimedScope);
        putScope(root.putObject("judgementScope"), judgementScope);
        ArrayNode rules = root.putArray("rules");
        byRule.forEach((id, t) -> {
            ObjectNode n = rules.addObject();
            n.put("actRuleId", id);
            n.put("name", ruleNames.get(id));
            n.put("claimed", ruleClaimed.get(id));
            n.put("cases", t.total());
            n.put("correct", t.correct);
            n.put("falsePositive", t.falsePositive);
            n.put("falseNegative", t.falseNegative);
            n.put("abstained", t.abstained);
            n.put("accuracy", round(t.accuracy()));
            n.put("consistent", t.consistent());
        });
        putResults(root.putArray("mistakes"), mistakes());
        putResults(root.putArray("abstentions"), abstentions());
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, root.toPrettyString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void putResults(ArrayNode mistakes, List<ActResult> results) {
        for (ActResult r : results) {
            ObjectNode n = mistakes.addObject();
            n.put("actRuleId", r.testCase().ruleId());
            n.put("ruleName", r.testCase().ruleName());
            n.put("expected", r.testCase().expected());
            n.put("actual", r.actual().name());
            n.put("verdict", r.verdict().name());
            n.put("url", r.testCase().url());
            n.put("selectors", String.join(", ", r.selectors()));
            n.put("messages", String.join(" | ", r.messages()));
        }
    }

    private static void putScope(ObjectNode n, Tally t) {
        n.put("cases", t.total());
        n.put("correct", t.correct);
        n.put("falsePositive", t.falsePositive);
        n.put("falseNegative", t.falseNegative);
        n.put("abstained", t.abstained);
        n.put("accuracy", round(t.accuracy()));
    }

    private static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    /**
     * EARL + JSON-LD implementation report, the format the ACT community accepts for publication on the
     * WAI implementations pages.
     */
    public void writeEarl(Path file, String toolVersion) {
        ObjectNode root = JSON.createObjectNode();
        root.put("@context", "https://act-rules.github.io/earl-context.json");
        ArrayNode graph = root.putArray("@graph");
        ObjectNode assertor = graph.addObject();
        assertor.put("@type", "Software");
        assertor.put("@id", "https://github.com/rookieInTraining/a11y-agent");
        assertor.put("name", "a11y-agent");
        assertor.put("release", toolVersion);
        for (ActResult r : results) {
            if (r.verdict() == ActVerdict.OUT_OF_SCOPE) {
                continue;
            }
            ObjectNode a = graph.addObject();
            a.put("@type", "Assertion");
            a.putObject("subject").put("@type", "TestSubject").put("source", r.testCase().url());
            a.putObject("test").put("@type", "TestCase").put("@id", r.testCase().url());
            ObjectNode result = a.putObject("result");
            result.put("@type", "TestResult");
            result.put("outcome", earlOutcome(r));
            result.put("description", String.join(" | ", r.messages()));
            a.putObject("assertedBy").put("@id", "https://github.com/rookieInTraining/a11y-agent");
            a.put("mode", "earl:automatic");
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, root.toPrettyString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String earlOutcome(ActResult r) {
        return switch (r.actual()) {
            case FAILED -> "earl:failed";
            case PASSED -> "earl:passed";
            case INAPPLICABLE -> "earl:inapplicable";
            case NEEDS_REVIEW, CANT_TELL -> "earl:cantTell";
        };
    }

    public void writeMarkdown(Path file) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ACT Rules benchmark: a11y-agent\n\n");
        sb.append("Corpus: ").append(totalCases).append(" W3C ACT Rules test cases. ");
        sb.append(outOfScope).append(" cases belong to ACT rules we do not implement.\n\n");
        sb.append("| Scope | Cases | Correct | False pos | False neg | Abstained | Accuracy |\n|---|---|---|---|---|---|---|\n");
        sb.append(mdScope("Claimed (fully automated)", claimedScope));
        sb.append(mdScope("Judgement (semi-automated)", judgementScope));
        sb.append("\n## Per ACT rule\n\n| ACT rule | Scope | Cases | Correct | FP | FN | Abstained | Accuracy | Name |\n|---|---|---|---|---|---|---|---|---|\n");
        List<Map.Entry<String, Tally>> rows = new ArrayList<>(byRule.entrySet());
        rows.sort(Comparator.<Map.Entry<String, Tally>, Boolean>comparing(e -> !ruleClaimed.get(e.getKey()))
                .thenComparing(e -> e.getKey()));
        for (var e : rows) {
            Tally t = e.getValue();
            sb.append("| `").append(e.getKey()).append("` | ").append(ruleClaimed.get(e.getKey()) ? "claimed" : "judgement")
              .append(" | ").append(t.total()).append(" | ").append(t.correct).append(" | ").append(t.falsePositive)
              .append(" | ").append(t.falseNegative).append(" | ").append(t.abstained).append(" | ")
              .append(String.format("%.0f%%", t.accuracy() * 100)).append(" | ").append(ruleNames.get(e.getKey())).append(" |\n");
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, sb.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String mdScope(String label, Tally t) {
        return "| " + label + " | " + t.total() + " | " + t.correct + " | " + t.falsePositive + " | " + t.falseNegative
                + " | " + t.abstained + " | **" + String.format("%.1f%%", t.accuracy() * 100) + "** |\n";
    }
}
