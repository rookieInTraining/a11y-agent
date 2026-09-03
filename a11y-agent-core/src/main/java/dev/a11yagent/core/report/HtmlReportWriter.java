package dev.a11yagent.core.report;

import static dev.a11yagent.core.report.Html.esc;

import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.PageAudit;
import dev.a11yagent.core.rules.Rules;
import dev.a11yagent.core.wcag.Criterion;
import dev.a11yagent.core.wcag.Wcag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Self-contained, accessible HTML report. Screenshot paths are relative to the artifacts directory. */
public final class HtmlReportWriter {

    private HtmlReportWriter() {
    }

    public static void write(AuditReport report, Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, render(report));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String render(AuditReport r) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        sb.append("<title>Accessibility audit: ").append(esc(r.name())).append("</title><style>").append(Html.CSS).append("</style></head><body>");
        sb.append("<header><h1>Accessibility audit: ").append(esc(r.name())).append("</h1>");
        sb.append("<p class=\"muted\">WCAG ").append(r.targetVersion().label()).append(" level ").append(r.targetLevel())
          .append(" · started ").append(esc(r.startedAt())).append(" · ").append(r.pages().size()).append(" page state(s) · ")
          .append(r.rulesRun().size()).append(" rules</p></header>");

        sb.append("<main><section aria-labelledby=\"summary\"><h2 id=\"summary\">Summary</h2><div class=\"grid\">");
        for (Outcome o : List.of(Outcome.FAILED, Outcome.NEEDS_REVIEW, Outcome.CANT_TELL, Outcome.PASSED, Outcome.INAPPLICABLE)) {
            sb.append("<div class=\"card ").append(o).append("\"><strong>").append(r.count(o)).append("</strong>").append(label(o)).append("</div>");
        }
        sb.append("</div></section>");

        sb.append("<section aria-labelledby=\"by-sc\"><h2 id=\"by-sc\">Results by success criterion</h2>");
        sb.append("<table><caption>Criteria in scope for WCAG ").append(r.targetVersion().label()).append(" ").append(r.targetLevel()).append("</caption>");
        sb.append("<thead><tr><th scope=\"col\">Criterion</th><th scope=\"col\">Level</th><th scope=\"col\">Rules</th><th scope=\"col\">Failed</th><th scope=\"col\">Review</th><th scope=\"col\">Passed</th><th scope=\"col\">Status</th></tr></thead><tbody>");
        for (Criterion c : Wcag.forConformance(r.targetVersion(), r.targetLevel())) {
            List<Finding> fs = r.findingsFor(c);
            long failed = fs.stream().filter(f -> f.outcome() == Outcome.FAILED).count();
            long review = fs.stream().filter(f -> f.outcome() == Outcome.NEEDS_REVIEW || f.outcome() == Outcome.CANT_TELL).count();
            long passed = fs.stream().filter(f -> f.outcome() == Outcome.PASSED).count();
            boolean covered = !Rules.pageRulesFor(c).isEmpty() || !Rules.journeyRulesFor(c).isEmpty();
            String status = !covered ? "Manual" : failed > 0 ? "Failed" : review > 0 ? "Needs review" : passed > 0 ? "Passed" : "Not applicable";
            String cls = failed > 0 ? "FAILED" : review > 0 ? "NEEDS_REVIEW" : passed > 0 ? "PASSED" : "INAPPLICABLE";
            sb.append("<tr><th scope=\"row\">").append(esc(c.id())).append(" ").append(esc(c.name())).append("</th><td>").append(c.levelIn(r.targetVersion()))
              .append("</td><td>").append(esc(String.join(", ", ruleIdsFor(c)))).append("</td><td>").append(failed).append("</td><td>").append(review)
              .append("</td><td>").append(passed).append("</td><td class=\"").append(cls).append("\">").append(status).append("</td></tr>");
        }
        sb.append("</tbody></table></section>");

        sb.append("<section aria-labelledby=\"issues\"><h2 id=\"issues\">Issues</h2>");
        List<Finding> issues = r.issues().sorted(Comparator.comparing((Finding f) -> f.outcome() == Outcome.FAILED ? 0 : 1).thenComparing(f -> -f.impact().ordinal())).toList();
        if (issues.isEmpty()) {
            sb.append("<p>No failures or items needing review.</p>");
        }
        Map<String, List<Finding>> byRule = new TreeMap<>();
        for (Finding f : issues) {
            byRule.computeIfAbsent(f.ruleId(), k -> new java.util.ArrayList<>()).add(f);
        }
        for (var e : byRule.entrySet()) {
            List<Finding> fs = e.getValue();
            String crit = fs.get(0).criteria().stream().map(Criterion::id).sorted().reduce((a, b) -> a + ", " + b).orElse("");
            sb.append("<details open><summary>").append(esc(e.getKey())).append(" <span class=\"muted\">(").append(esc(crit)).append(") · ").append(fs.size()).append(" item(s)</span></summary>");
            sb.append("<p class=\"muted\">").append(esc(Rules.pageRule(e.getKey()).map(x -> x.description()).orElseGet(() -> Rules.journeyRule(e.getKey()).map(x -> x.description()).orElse("")))).append("</p>");
            int i = 0;
            for (Finding f : fs) {
                if (i++ >= 50) {
                    sb.append("<p class=\"muted\">…").append(fs.size() - 50).append(" more not shown.</p>");
                    break;
                }
                sb.append("<article class=\"card\"><p><span class=\"badge ").append(f.outcome()).append("\">").append(label(f.outcome())).append("</span> ")
                  .append("<span class=\"badge\">").append(f.impact().name().toLowerCase()).append("</span> ")
                  .append(f.step() != null ? "<span class=\"muted\">step: " + esc(f.step()) + "</span> " : "")
                  .append("</p><p>").append(esc(f.message())).append("</p>");
                if (f.target() != null && !"html".equals(f.target().selector())) {
                    sb.append("<p><code>").append(esc(f.target().selector())).append("</code></p>");
                    if (!f.target().html().isBlank()) {
                        sb.append("<pre>").append(esc(f.target().html())).append("</pre>");
                    }
                }
                if (f.evidence().model() != null) {
                    sb.append("<p class=\"muted\">AI judgement by ").append(esc(f.evidence().model())).append(" (confidence ").append(String.format("%.2f", f.evidence().confidence())).append("): ").append(esc(f.evidence().rationale())).append("</p>");
                }
                if (f.evidence().screenshot() != null) {
                    sb.append("<p><img class=\"shot\" src=\"").append(esc(f.evidence().screenshot())).append("\" alt=\"Screenshot of ").append(esc(f.target().selector())).append(" for rule ").append(esc(f.ruleId())).append("\"></p>");
                }
                sb.append("</article>");
            }
            sb.append("</details>");
        }
        sb.append("</section>");

        sb.append("<section aria-labelledby=\"pages\"><h2 id=\"pages\">Page states</h2><table><thead><tr><th scope=\"col\">Step</th><th scope=\"col\">URL</th><th scope=\"col\">Title</th><th scope=\"col\">Failed</th><th scope=\"col\">Review</th><th scope=\"col\">Passed</th></tr></thead><tbody>");
        for (PageAudit p : r.pages()) {
            sb.append("<tr><th scope=\"row\">").append(esc(p.step())).append("</th><td>").append(esc(p.url())).append("</td><td>").append(esc(p.title())).append("</td><td>")
              .append(p.count(Outcome.FAILED)).append("</td><td>").append(p.count(Outcome.NEEDS_REVIEW) + p.count(Outcome.CANT_TELL)).append("</td><td>").append(p.count(Outcome.PASSED)).append("</td></tr>");
        }
        sb.append("</tbody></table></section></main>");
        sb.append("<footer class=\"muted\"><p>Generated by a11y-agent. Deterministic rules have confidence 1.0; AI judgements quote the model and its rationale and should be confirmed by a human auditor before being used in a conformance claim.</p></footer>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static List<String> ruleIdsFor(Criterion c) {
        List<String> ids = new java.util.ArrayList<>();
        Rules.pageRulesFor(c).forEach(r -> ids.add(r.id()));
        Rules.journeyRulesFor(c).forEach(r -> ids.add(r.id()));
        return ids;
    }

    static String label(Outcome o) {
        return switch (o) {
            case PASSED -> "Passed";
            case FAILED -> "Failed";
            case INAPPLICABLE -> "Inapplicable";
            case CANT_TELL -> "Can't tell";
            case NEEDS_REVIEW -> "Needs review";
        };
    }
}
