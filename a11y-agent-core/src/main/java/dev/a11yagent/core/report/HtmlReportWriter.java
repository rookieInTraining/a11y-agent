package dev.a11yagent.core.report;

import static dev.a11yagent.core.report.Html.esc;

import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.PageAudit;
import dev.a11yagent.core.rules.AiAssist;
import dev.a11yagent.core.rules.Rule;
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
        List<Finding> ai = r.aiJudgements();
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        sb.append("<title>Accessibility audit: ").append(esc(r.name())).append("</title><style>").append(Html.CSS).append("</style></head><body>");
        sb.append("<header><h1>Accessibility audit: ").append(esc(r.name())).append("</h1>");
        sb.append("<p class=\"muted\">WCAG ").append(r.targetVersion().label()).append(" level ").append(r.targetLevel())
          .append(" · started ").append(esc(r.startedAt())).append(" · ").append(r.pages().size()).append(" page state(s) · ")
          .append(r.rulesRun().size()).append(" rules");
        if (r.aiConfigured()) {
            sb.append(" · vision/language model ").append(esc(r.aiModel())).append(" · ").append(ai.size()).append(" AI judgement")
                    .append(ai.size() == 1 ? "" : "s");
        } else {
            sb.append(" · AI not configured");
        }
        sb.append("</p></header>");

        sb.append("<main>");
        if (r.hasVideo()) {
            sb.append("<section aria-labelledby=\"recording\"><h2 id=\"recording\">Audit recording</h2>");
            sb.append("<p class=\"muted\">WebM of the page while rules ran (keyboard probes, highlighted issues). Confirm visually; it is not a conformance artefact.</p>");
            sb.append("<video class=\"audit\" controls playsinline preload=\"metadata\">");
            sb.append("<source src=\"").append(esc(r.video())).append("\" type=\"video/webm\">");
            sb.append("Your browser cannot play this WebM. Open <a href=\"").append(esc(r.video())).append("\">").append(esc(r.video())).append("</a>.");
            sb.append("</video></section>");
        }
        sb.append("<section aria-labelledby=\"summary\"><h2 id=\"summary\">Summary</h2><div class=\"grid\">");
        for (Outcome o : List.of(Outcome.FAILED, Outcome.NEEDS_REVIEW, Outcome.CANT_TELL, Outcome.PASSED, Outcome.INAPPLICABLE)) {
            sb.append("<div class=\"card ").append(o).append("\"><strong>").append(r.count(o)).append("</strong>").append(label(o)).append("</div>");
        }
        sb.append("<div class=\"card\"><strong>").append(ai.size()).append("</strong>AI judgements</div>");
        sb.append("</div></section>");

        analysisSection(sb, r, ai);

        sb.append("<section aria-labelledby=\"by-sc\"><h2 id=\"by-sc\">Results by success criterion</h2>");
        sb.append("<table><caption>Criteria in scope for WCAG ").append(r.targetVersion().label()).append(" ").append(r.targetLevel()).append("</caption>");
        sb.append("<thead><tr><th scope=\"col\">Criterion</th><th scope=\"col\">Level</th><th scope=\"col\">Rules</th><th scope=\"col\">Analysis</th><th scope=\"col\">Failed</th><th scope=\"col\">Review</th><th scope=\"col\">Passed</th><th scope=\"col\">Status</th></tr></thead><tbody>");
        for (Criterion c : Wcag.forConformance(r.targetVersion(), r.targetLevel())) {
            List<Finding> fs = r.findingsFor(c);
            long failed = fs.stream().filter(f -> f.outcome() == Outcome.FAILED).count();
            long review = fs.stream().filter(f -> f.outcome() == Outcome.NEEDS_REVIEW || f.outcome() == Outcome.CANT_TELL).count();
            long passed = fs.stream().filter(f -> f.outcome() == Outcome.PASSED).count();
            boolean covered = !Rules.pageRulesFor(c).isEmpty() || !Rules.journeyRulesFor(c).isEmpty();
            String status = !covered ? "Manual" : failed > 0 ? "Failed" : review > 0 ? "Needs review" : passed > 0 ? "Passed" : "Not applicable";
            String cls = failed > 0 ? "FAILED" : review > 0 ? "NEEDS_REVIEW" : passed > 0 ? "PASSED" : "INAPPLICABLE";
            sb.append("<tr><th scope=\"row\">").append(esc(c.id())).append(" ").append(esc(c.name())).append("</th><td>").append(c.levelIn(r.targetVersion()))
              .append("</td><td>").append(esc(String.join(", ", ruleIdsFor(c)))).append("</td><td>").append(esc(analysisForCriterion(c)))
              .append("</td><td>").append(failed).append("</td><td>").append(review)
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
            AiAssist ruleAssist = Rules.aiAssist(e.getKey());
            long judged = fs.stream().filter(f -> f.evidence().aiJudged()).count();
            sb.append("<details open><summary>").append(esc(e.getKey())).append(" <span class=\"muted\">(").append(esc(crit)).append(") · ")
                    .append(fs.size()).append(" item(s)</span> ").append(assistBadge(ruleAssist));
            if (judged > 0) {
                sb.append(" <span class=\"muted\">").append(judged).append(" judged by AI</span>");
            }
            sb.append("</summary>");
            sb.append("<p class=\"muted\">").append(esc(Rules.pageRule(e.getKey()).map(Rule::description).orElseGet(() -> Rules.journeyRule(e.getKey()).map(x -> x.description()).orElse(""))))
                    .append(" ").append(esc(assistHint(ruleAssist))).append("</p>");
            int i = 0;
            for (Finding f : fs) {
                if (i++ >= 50) {
                    sb.append("<p class=\"muted\">…").append(fs.size() - 50).append(" more not shown.</p>");
                    break;
                }
                findingCard(sb, f);
            }
            sb.append("</details>");
        }
        sb.append("</section>");

        if (!ai.isEmpty()) {
            aiJudgementsSection(sb, ai);
        }

        sb.append("<section aria-labelledby=\"pages\"><h2 id=\"pages\">Page states</h2><table><thead><tr><th scope=\"col\">Step</th><th scope=\"col\">URL</th><th scope=\"col\">Title</th><th scope=\"col\">Failed</th><th scope=\"col\">Review</th><th scope=\"col\">Passed</th></tr></thead><tbody>");
        for (PageAudit p : r.pages()) {
            sb.append("<tr><th scope=\"row\">").append(esc(p.step())).append("</th><td>").append(esc(p.url())).append("</td><td>").append(esc(p.title())).append("</td><td>")
              .append(p.count(Outcome.FAILED)).append("</td><td>").append(p.count(Outcome.NEEDS_REVIEW) + p.count(Outcome.CANT_TELL)).append("</td><td>").append(p.count(Outcome.PASSED)).append("</td></tr>");
        }
        sb.append("</tbody></table></section></main>");
        sb.append("<footer class=\"muted\"><p>Generated by a11y-agent. Deterministic rules have confidence 1.0. AI-only and AI-enhanced verdicts quote the model, confidence and rationale and must be confirmed by a human auditor before being used in a conformance claim.</p></footer>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static void analysisSection(StringBuilder sb, AuditReport r, List<Finding> ai) {
        sb.append("<section aria-labelledby=\"analysis\"><h2 id=\"analysis\">How this audit was produced</h2>");
        sb.append("<div class=\"ai-callout\">");
        if (r.aiConfigured()) {
            sb.append("<p><strong>Vision/language model:</strong> ").append(esc(r.aiModel()))
                    .append(". ").append(ai.size()).append(" finding").append(ai.size() == 1 ? " was" : "s were")
                    .append(" judged by the model. AI verdicts are quoted below with confidence; they are not conformance claims.</p>");
            long reviewed = r.allFindings().stream()
                    .filter(f -> Boolean.TRUE.equals(f.evidence().data().get("videoEnrichment")))
                    .count();
            if (reviewed > 0) {
                sb.append("<p>After the probes finished, ").append(reviewed)
                        .append(" leftover finding").append(reviewed == 1 ? " was" : "s were")
                        .append(" reviewed against the audit recording (frames plus finding text). Deterministic pass/fail was not changed.</p>");
            }
        } else {
            sb.append("<p><strong>No vision/language model was configured.</strong> AI-only and AI-enhanced rules ran as heuristics only. Configure a model (CLI <code>--ai</code> or <code>A11yConfig.modelClient</code>) to have a model look at remaining alt-text and ambiguous focus indicators.</p>");
        }
        sb.append("<p><span class=\"badge AI_ONLY\">AI-only</span> — the model <em>is</em> the check (heuristics only pre-filter, or run when no model is set). ");
        sb.append("<span class=\"badge ENHANCED\">AI-enhanced</span> — a deterministic probe runs first; the model judges leftovers. ");
        sb.append("<span class=\"badge NONE\">Deterministic</span> — never calls a model.</p>");
        sb.append("</div>");

        sb.append("<table><caption>Rules executed in this run</caption>");
        sb.append("<thead><tr><th scope=\"col\">Rule</th><th scope=\"col\">Kind</th><th scope=\"col\">Analysis</th><th scope=\"col\">AI judgements</th><th scope=\"col\">Criteria</th></tr></thead><tbody>");
        Map<String, Long> judgedByRule = new TreeMap<>();
        for (Finding f : ai) {
            judgedByRule.merge(f.ruleId(), 1L, Long::sum);
        }
        for (String id : r.rulesRun()) {
            AiAssist assist = Rules.aiAssist(id);
            String kind = Rules.pageRule(id).map(rule -> rule.kind().name()).orElse("CROSS_STEP");
            String criteria = Rules.pageRule(id).map(rule -> rule.criteria().stream().map(Criterion::id).sorted().reduce((a, b) -> a + ", " + b).orElse(""))
                    .orElseGet(() -> Rules.journeyRule(id).map(jr -> jr.criteria().stream().map(Criterion::id).sorted().reduce((a, b) -> a + ", " + b).orElse("")).orElse(""));
            long n = judgedByRule.getOrDefault(id, 0L);
            sb.append("<tr><th scope=\"row\"><code>").append(esc(id)).append("</code></th><td>").append(esc(kind.replace('_', ' ').toLowerCase()))
                    .append("</td><td>").append(assistBadge(assist)).append("</td><td>").append(n)
                    .append("</td><td>").append(esc(criteria)).append("</td></tr>");
        }
        sb.append("</tbody></table></section>");
    }

    private static void aiJudgementsSection(StringBuilder sb, List<Finding> ai) {
        sb.append("<section aria-labelledby=\"ai-judgements\"><h2 id=\"ai-judgements\">AI judgements</h2>");
        sb.append("<p>Every finding the model actually looked at, including passes. Confirm these before treating them as WCAG failures or supports.</p>");
        Map<String, List<Finding>> byRule = new TreeMap<>();
        for (Finding f : ai) {
            byRule.computeIfAbsent(f.ruleId(), k -> new java.util.ArrayList<>()).add(f);
        }
        for (var e : byRule.entrySet()) {
            AiAssist assist = Rules.aiAssist(e.getKey());
            sb.append("<details open><summary>").append(esc(e.getKey())).append(" ").append(assistBadge(assist))
                    .append(" <span class=\"muted\">").append(e.getValue().size()).append(" judgement")
                    .append(e.getValue().size() == 1 ? "" : "s").append("</span></summary>");
            for (Finding f : e.getValue()) {
                findingCard(sb, f);
            }
            sb.append("</details>");
        }
        sb.append("</section>");
    }

    private static void findingCard(StringBuilder sb, Finding f) {
        AiAssist findingAssist = Rules.ofFinding(f);
        sb.append("<article class=\"card\"><p><span class=\"badge ").append(f.outcome()).append("\">").append(label(f.outcome())).append("</span> ")
          .append("<span class=\"badge\">").append(f.impact().name().toLowerCase()).append("</span> ")
          .append(assistBadge(findingAssist));
        if (f.step() != null) {
            sb.append(" <span class=\"muted\">step: ").append(esc(f.step())).append("</span>");
        }
        sb.append("</p><p>").append(esc(f.message())).append("</p>");
        if (f.target() != null && !"html".equals(f.target().selector())) {
            sb.append("<p><code>").append(esc(f.target().selector())).append("</code></p>");
            if (!f.target().html().isBlank()) {
                sb.append("<pre>").append(esc(f.target().html())).append("</pre>");
            }
        }
        if (f.evidence().aiJudged()) {
            sb.append("<blockquote class=\"ai\"><p><strong>AI judgement</strong> by ").append(esc(f.evidence().model()))
                    .append(" · confidence ").append(String.format("%.2f", f.evidence().confidence()));
            Object verdict = f.evidence().data().get("aiVerdict");
            if (verdict != null) {
                sb.append(" · model result ").append(esc(verdict));
            }
            sb.append("</p><p>").append(esc(f.evidence().rationale())).append("</p></blockquote>");
        }
        if (f.evidence().screenshot() != null) {
            sb.append("<figure class=\"shot\"><img class=\"shot\" src=\"").append(esc(f.evidence().screenshot()))
                    .append("\" alt=\"Highlighted screenshot of ").append(esc(f.target().selector())).append(" for rule ").append(esc(f.ruleId())).append("\">");
            sb.append("<figcaption>Highlighted target <code>").append(esc(f.target().selector())).append("</code></figcaption></figure>");
        }
        sb.append("</article>");
    }

    private static String assistBadge(AiAssist assist) {
        return "<span class=\"badge " + assist.name() + "\">" + esc(assist.label()) + "</span>";
    }

    private static String assistHint(AiAssist assist) {
        return switch (assist) {
            case AI_ONLY -> "AI-only rule: the model judges remaining informative images; file-name and generic-word failures below are still heuristic.";
            case ENHANCED -> "AI-enhanced rule: the model is asked only when the deterministic probe is ambiguous.";
            case NONE -> "Deterministic rule: no model involved.";
        };
    }

    private static String analysisForCriterion(Criterion c) {
        java.util.LinkedHashSet<String> labels = new java.util.LinkedHashSet<>();
        Rules.pageRulesFor(c).forEach(rule -> labels.add(rule.aiAssist().label()));
        Rules.journeyRulesFor(c).forEach(jr -> labels.add(AiAssist.NONE.label()));
        return labels.isEmpty() ? "Manual" : String.join(" + ", labels);
    }

    private static List<String> ruleIdsFor(Criterion c) {
        List<String> ids = new java.util.ArrayList<>();
        Rules.pageRulesFor(c).forEach(rule -> ids.add(rule.id()));
        Rules.journeyRulesFor(c).forEach(jr -> ids.add(jr.id()));
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
