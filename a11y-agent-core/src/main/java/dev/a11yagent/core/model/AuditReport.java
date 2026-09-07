package dev.a11yagent.core.model;

import dev.a11yagent.core.wcag.Criterion;
import dev.a11yagent.core.wcag.Level;
import dev.a11yagent.core.wcag.WcagVersion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The complete result of an audit or a journey.
 *
 * @param name            audit / journey name
 * @param startedAt       when the audit started
 * @param finishedAt      when the audit finished
 * @param targetVersion   WCAG version used for conformance evaluation
 * @param targetLevel     WCAG level used for conformance evaluation
 * @param rulesRun        ids of the rules that were executed
 * @param pages           per page-state results
 * @param journeyFindings findings produced by cross-step rules (empty for single-page audits)
 * @param aiModel         configured vision/language model id, or {@code null} when AI was off
 * @param video           relative path of the WebM recording, or {@code null}
 */
public record AuditReport(
        String name,
        Instant startedAt,
        Instant finishedAt,
        WcagVersion targetVersion,
        Level targetLevel,
        Set<String> rulesRun,
        List<PageAudit> pages,
        List<Finding> journeyFindings,
        String aiModel,
        String video) {

    public AuditReport {
        pages = List.copyOf(pages);
        journeyFindings = List.copyOf(journeyFindings);
        rulesRun = Set.copyOf(rulesRun);
        if (aiModel != null && aiModel.isBlank()) {
            aiModel = null;
        }
        if (video != null && video.isBlank()) {
            video = null;
        }
    }

    public AuditReport(
            String name,
            Instant startedAt,
            Instant finishedAt,
            WcagVersion targetVersion,
            Level targetLevel,
            Set<String> rulesRun,
            List<PageAudit> pages,
            List<Finding> journeyFindings) {
        this(name, startedAt, finishedAt, targetVersion, targetLevel, rulesRun, pages, journeyFindings, null, null);
    }

    public AuditReport(
            String name,
            Instant startedAt,
            Instant finishedAt,
            WcagVersion targetVersion,
            Level targetLevel,
            Set<String> rulesRun,
            List<PageAudit> pages,
            List<Finding> journeyFindings,
            String aiModel) {
        this(name, startedAt, finishedAt, targetVersion, targetLevel, rulesRun, pages, journeyFindings, aiModel, null);
    }

    /** Every finding from every page plus journey-level findings. */
    public List<Finding> allFindings() {
        List<Finding> all = new ArrayList<>();
        pages.forEach(p -> all.addAll(p.findings()));
        all.addAll(journeyFindings);
        return all;
    }

    public Stream<Finding> issues() {
        return allFindings().stream().filter(f -> f.outcome().isIssue());
    }

    public long count(Outcome outcome) {
        return allFindings().stream().filter(f -> f.outcome() == outcome).count();
    }

    /** Findings that touch the given criterion. */
    public List<Finding> findingsFor(Criterion criterion) {
        return allFindings().stream().filter(f -> f.criteria().contains(criterion)).toList();
    }

    public boolean hasFailures() {
        return allFindings().stream().anyMatch(f -> f.outcome() == Outcome.FAILED);
    }

    public boolean aiConfigured() {
        return aiModel != null;
    }

    /** Findings whose evidence was produced by a vision/language model. */
    public List<Finding> aiJudgements() {
        return allFindings().stream().filter(f -> f.evidence().aiJudged()).toList();
    }

    public boolean hasVideo() {
        return video != null;
    }
}
