package dev.a11yagent.core.vpat;

import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.rules.Rules;
import dev.a11yagent.core.wcag.Criterion;
import dev.a11yagent.core.wcag.Level;
import dev.a11yagent.core.wcag.Wcag;
import dev.a11yagent.core.wcag.WcagVersion;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a VPAT 2.5 (WCAG edition) Accessibility Conformance Report from one or more audit reports.
 *
 * <p>Mapping from evidence to conformance terms is deliberately conservative:
 * <ul>
 *   <li>any FAILED finding → "Does Not Support" (no passes) or "Partially Supports" (some passes);</li>
 *   <li>only NEEDS_REVIEW / CANT_TELL findings → "Not Evaluated" with the review items listed, because an
 *       unconfirmed heuristic or AI verdict must not become a conformance claim;</li>
 *   <li>only PASSED (and INAPPLICABLE) → "Supports" with the rules that produced the evidence;</li>
 *   <li>only INAPPLICABLE → "Not Applicable";</li>
 *   <li>criteria without automated coverage → "Not Evaluated", flagged for manual testing.</li>
 * </ul>
 */
public final class VpatGenerator {

    private VpatGenerator() {
    }

    public static VpatDocument generate(List<AuditReport> reports, VpatOptions options) {
        if (reports.isEmpty()) {
            throw new IllegalArgumentException("At least one audit report is required");
        }
        WcagVersion version = reports.stream().map(AuditReport::targetVersion).max(Enum::compareTo).orElseThrow();
        Level level = reports.stream().map(AuditReport::targetLevel).max(Enum::compareTo).orElseThrow();
        List<Finding> all = new ArrayList<>();
        Set<String> pages = new LinkedHashSet<>();
        for (AuditReport r : reports) {
            all.addAll(r.allFindings());
            r.pages().forEach(p -> pages.add(p.url()));
        }
        List<VpatEntry> entries = new ArrayList<>();
        for (Criterion c : Wcag.forConformance(version, level)) {
            entries.add(entry(c, version, all));
        }
        return new VpatDocument(options, version, level, entries, new ArrayList<>(pages), reports.size());
    }

    public static VpatDocument generate(AuditReport report, VpatOptions options) {
        return generate(List.of(report), options);
    }

    static VpatEntry entry(Criterion c, WcagVersion version, List<Finding> all) {
        boolean automated = !Rules.pageRulesFor(c).isEmpty() || !Rules.journeyRulesFor(c).isEmpty();
        List<Finding> fs = all.stream().filter(f -> f.criteria().contains(c)).toList();
        long failed = count(fs, Outcome.FAILED);
        long review = count(fs, Outcome.NEEDS_REVIEW) + count(fs, Outcome.CANT_TELL);
        long passed = count(fs, Outcome.PASSED);
        long inapplicable = count(fs, Outcome.INAPPLICABLE);
        Level lvl = c.levelIn(version);

        Conformance conf;
        StringBuilder remarks = new StringBuilder();
        if (!automated) {
            conf = Conformance.NOT_EVALUATED;
            remarks.append("No automated rule covers this criterion; requires manual evaluation.");
        } else if (fs.isEmpty()) {
            conf = Conformance.NOT_EVALUATED;
            remarks.append("Covered by rules ").append(ruleNames(c)).append(" but they were not executed in the supplied reports.");
        } else if (failed > 0) {
            conf = passed > 0 ? Conformance.PARTIALLY_SUPPORTS : Conformance.DOES_NOT_SUPPORT;
            remarks.append(failed).append(" failure(s) found by ").append(rulesIn(fs, Outcome.FAILED)).append(". ");
            remarks.append(examples(fs, Outcome.FAILED, 3));
            if (review > 0) {
                remarks.append(" Additionally ").append(review).append(" item(s) need manual review.");
            }
        } else if (review > 0) {
            conf = Conformance.NOT_EVALUATED;
            remarks.append("Automated evidence inconclusive: ").append(review).append(" item(s) flagged for manual review by ")
                    .append(rulesIn(fs, Outcome.NEEDS_REVIEW, Outcome.CANT_TELL)).append(". ").append(examples(fs, Outcome.NEEDS_REVIEW, 2));
            if (passed > 0) {
                remarks.append(" ").append(passed).append(" target(s) passed.");
            }
        } else if (passed > 0) {
            conf = Conformance.SUPPORTS;
            remarks.append("All ").append(passed).append(" evaluated target(s) passed rules ").append(rulesIn(fs, Outcome.PASSED))
                    .append(". Automated rules cover the aspects listed; confirm remaining aspects manually.");
        } else if (inapplicable > 0) {
            conf = Conformance.NOT_APPLICABLE;
            remarks.append("No content on the evaluated pages is subject to this criterion (").append(ruleNames(c)).append(").");
        } else {
            conf = Conformance.NOT_EVALUATED;
            remarks.append("No usable evidence.");
        }
        return new VpatEntry(c, lvl, conf, remarks.toString().trim(), failed, review, passed, automated);
    }

    private static long count(List<Finding> fs, Outcome o) {
        return fs.stream().filter(f -> f.outcome() == o).count();
    }

    private static String ruleNames(Criterion c) {
        Set<String> ids = new LinkedHashSet<>();
        Rules.pageRulesFor(c).forEach(r -> ids.add(r.id()));
        Rules.journeyRulesFor(c).forEach(r -> ids.add(r.id()));
        return String.join(", ", ids);
    }

    private static String rulesIn(List<Finding> fs, Outcome... outcomes) {
        Set<Outcome> set = Set.of(outcomes);
        return fs.stream().filter(f -> set.contains(f.outcome())).map(Finding::ruleId).distinct().sorted().collect(Collectors.joining(", "));
    }

    private static String examples(List<Finding> fs, Outcome o, int max) {
        List<String> ex = fs.stream().filter(f -> f.outcome() == o).limit(max)
                .map(f -> "[" + (f.step() != null ? f.step() + " · " : "") + short_(f.target().selector()) + "] " + short_(f.message()))
                .toList();
        return ex.isEmpty() ? "" : "Examples: " + String.join(" | ", ex);
    }

    private static String short_(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 160 ? s.substring(0, 157) + "..." : s;
    }
}
