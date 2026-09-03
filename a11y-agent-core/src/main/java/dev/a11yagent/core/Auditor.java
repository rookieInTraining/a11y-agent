package dev.a11yagent.core;

import dev.a11yagent.core.ai.Judge;
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.driver.PageDriver;
import dev.a11yagent.core.journey.Journey;
import dev.a11yagent.core.journey.JourneyRule;
import dev.a11yagent.core.journey.StepSnapshot;
import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.PageAudit;
import dev.a11yagent.core.rules.ArtifactStore;
import dev.a11yagent.core.rules.Rule;
import dev.a11yagent.core.rules.RuleContext;
import dev.a11yagent.core.rules.Rules;
import dev.a11yagent.core.wcag.Criterion;
import dev.a11yagent.core.wcag.Wcag;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Driver-agnostic orchestrator. Runs page rules against the current state of a {@link PageDriver},
 * drives {@link Journey}s and evaluates cross-step rules. Framework adapters (Playwright today,
 * Selenium later) only need to provide a {@link PageDriver}.
 */
public final class Auditor {

    private final PageDriver driver;
    private final A11yConfig config;
    private final ArtifactStore artifacts;
    private final Judge judge;
    private Consumer<String> progress = s -> { };

    public Auditor(PageDriver driver, A11yConfig config) {
        this.driver = driver;
        this.config = config;
        this.artifacts = new ArtifactStore(config.artifactsDir());
        this.judge = config.modelClient().map(c -> new Judge(c, config.maxAiJudgements())).orElse(null);
    }

    public Auditor onProgress(Consumer<String> listener) {
        this.progress = listener == null ? s -> { } : listener;
        return this;
    }

    public A11yConfig config() {
        return config;
    }

    /** Full audit of the current page state with every enabled rule in scope of the configured version/level. */
    public AuditReport auditPage() {
        return auditPage("page");
    }

    public AuditReport auditPage(String name) {
        Instant start = Instant.now();
        List<Rule> rules = enabledPageRules();
        PageAudit page = auditState(name, rules, null);
        return new AuditReport(name, start, Instant.now(), config.targetVersion(), config.targetLevel(),
                ruleIds(rules), List.of(page), List.of());
    }

    /**
     * Runs a single check on the current page. {@code ruleOrCriterion} is either a rule id
     * ({@code focus-visible}) or a success criterion id ({@code 2.4.7}).
     */
    public AuditReport check(String ruleOrCriterion) {
        Instant start = Instant.now();
        List<Rule> rules = resolvePageRules(ruleOrCriterion);
        if (rules.isEmpty()) {
            Optional<Criterion> c = Wcag.find(ruleOrCriterion);
            if (c.isPresent()) {
                Finding none = Finding.builder("coverage").criteria(Set.of(c.get())).outcome(Outcome.CANT_TELL).impact(Impact.MINOR)
                        .message("No automated rule covers " + c.get() + "; manual evaluation required.")
                        .evidence(Evidence.deterministic("not covered")).url(driver.url()).build();
                PageAudit page = new PageAudit("page", driver.url(), driver.title(), null, List.of(none));
                return new AuditReport("check " + ruleOrCriterion, start, Instant.now(), config.targetVersion(), config.targetLevel(), Set.of(), List.of(page), List.of());
            }
            throw new IllegalArgumentException("Unknown rule or success criterion: " + ruleOrCriterion);
        }
        PageAudit page = auditState("page", rules, null);
        return new AuditReport("check " + ruleOrCriterion, start, Instant.now(), config.targetVersion(), config.targetLevel(),
                ruleIds(rules), List.of(page), List.of());
    }

    /**
     * Runs a specific set of rules (by id) against the current page in a single shared context, so
     * expensive artefacts such as the keyboard traversal and the accessibility tree are computed once.
     * Unknown ids are ignored. Used by the benchmark runner, which must run exactly the rules mapped to
     * the test case under evaluation.
     */
    public PageAudit checkRules(Collection<String> ruleIds, String name) {
        List<Rule> rules = ruleIds.stream().map(Rules::pageRule).flatMap(Optional::stream).toList();
        return auditState(name, rules, null);
    }

    /** Runs a journey: audits the landing page (if a start URL is set) and every step, then cross-step rules. */
    public AuditReport runJourney(Journey journey) {
        Instant start = Instant.now();
        List<Rule> rules = enabledPageRules();
        List<PageAudit> pages = new ArrayList<>();
        List<StepSnapshot> snapshots = new ArrayList<>();
        if (journey.startUrl() != null) {
            progress.accept("navigate " + journey.startUrl());
            driver.navigate(journey.startUrl());
            pages.add(auditState("start", rules, snapshots));
        }
        for (Journey.Step step : journey.steps()) {
            progress.accept("step " + step.name());
            step.action().run();
            pages.add(auditState(step.name(), rules, snapshots));
        }
        List<Finding> journeyFindings = new ArrayList<>();
        Set<String> ran = new LinkedHashSet<>(ruleIds(rules));
        for (JourneyRule jr : Rules.journeyRules()) {
            if (!config.ruleEnabled(jr.id()) || !inScope(jr.criteria())) {
                continue;
            }
            progress.accept("journey rule " + jr.id());
            ran.add(jr.id());
            try {
                journeyFindings.addAll(jr.evaluate(snapshots));
            } catch (RuntimeException e) {
                journeyFindings.add(error(jr.id(), jr.criteria(), e, null));
            }
        }
        return new AuditReport(journey.name(), start, Instant.now(), config.targetVersion(), config.targetLevel(), ran, pages, journeyFindings);
    }

    /** Public so adapters can build custom flows (e.g. audit after each Playwright navigation). */
    public PageAudit auditState(String stepName, List<Rule> rules, List<StepSnapshot> snapshots) {
        RuleContext ctx = new RuleContext(driver, config, judge, artifacts, stepName);
        String url = driver.url();
        String title = driver.title();
        String shot = null;
        if (config.screenshots()) {
            try {
                shot = artifacts.savePng("page-" + stepName, driver.screenshot(true));
            } catch (RuntimeException ignored) {
                // screenshots are best effort
            }
        }
        List<Finding> findings = new ArrayList<>();
        for (Rule rule : rules) {
            progress.accept("rule " + rule.id());
            try {
                for (Finding f : rule.evaluate(ctx)) {
                    findings.add(f.inStep(stepName, f.url() == null ? url : f.url()));
                }
            } catch (RuntimeException e) {
                findings.add(error(rule.id(), rule.criteria(), e, url).inStep(stepName, url));
            }
        }
        if (snapshots != null) {
            try {
                Map<String, Object> data = ctx.inPage().snapshot();
                snapshots.add(new StepSnapshot(snapshots.size(), stepName, url, title, data));
            } catch (RuntimeException e) {
                findings.add(error("snapshot", Set.of(), e, url).inStep(stepName, url));
            }
        }
        return new PageAudit(stepName, url, title, shot, findings);
    }

    public List<Rule> enabledPageRules() {
        return Rules.pageRules().stream()
                .filter(r -> config.ruleEnabled(r.id()))
                .filter(r -> inScope(r.criteria()))
                .toList();
    }

    private List<Rule> resolvePageRules(String ruleOrCriterion) {
        Optional<Rule> byId = Rules.pageRule(ruleOrCriterion);
        if (byId.isPresent()) {
            return List.of(byId.get());
        }
        return Wcag.find(ruleOrCriterion).map(Rules::pageRulesFor).orElse(List.of());
    }

    private boolean inScope(Set<Criterion> criteria) {
        return criteria.isEmpty() || criteria.stream().anyMatch(c ->
                c.existsIn(config.targetVersion()) && c.levelIn(config.targetVersion()).includedIn(config.targetLevel()));
    }

    private static Set<String> ruleIds(List<Rule> rules) {
        Set<String> ids = new LinkedHashSet<>();
        rules.forEach(r -> ids.add(r.id()));
        return ids;
    }

    private static Finding error(String ruleId, Set<Criterion> criteria, RuntimeException e, String url) {
        String msg = "Rule " + ruleId + " failed to execute: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return Finding.builder(ruleId).criteria(criteria).outcome(Outcome.CANT_TELL).impact(Impact.MINOR)
                .message(msg).evidence(Evidence.deterministic(msg)).url(url).build();
    }
}
