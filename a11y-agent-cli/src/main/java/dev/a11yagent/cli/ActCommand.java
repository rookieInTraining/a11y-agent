package dev.a11yagent.cli;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import dev.a11yagent.core.Auditor;
import dev.a11yagent.core.benchmark.ActCase;
import dev.a11yagent.core.benchmark.ActCorpus;
import dev.a11yagent.core.benchmark.ActMapping;
import dev.a11yagent.core.benchmark.ActResult;
import dev.a11yagent.core.benchmark.ActScorer;
import dev.a11yagent.core.benchmark.ActSummary;
import dev.a11yagent.core.benchmark.ActVerdict;
import dev.a11yagent.core.benchmark.RuleSelector;
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.PageAudit;
import dev.a11yagent.core.rules.Rule;
import dev.a11yagent.core.rules.Rules;
import dev.a11yagent.core.wcag.Level;
import dev.a11yagent.core.wcag.WcagVersion;
import dev.a11yagent.playwright.Browsers;
import dev.a11yagent.playwright.PlaywrightDriver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Runs the W3C ACT Rules test cases and scores our implementation the way the ACT community does:
 * every test case of an ACT rule we map to is evaluated with only the rules mapped to it, and the
 * outcome is compared with the expected one.
 */
@Command(name = "act", description = "Benchmark against the W3C ACT Rules test cases and emit an EARL implementation report.",
        mixinStandardHelpOptions = true)
final class ActCommand implements Callable<Integer> {

    @Option(names = "--testcases", required = true, description = "ACT testcases.json (W3C file or a local mirror manifest).")
    Path testcases;

    @Option(names = "--corpus", description = "Directory containing a local mirror of the test case pages, laid out by URL path. Without it the live w3.org URLs are used.")
    Path corpus;

    @Option(names = {"-o", "--out"}, defaultValue = "act-results", description = "Output directory (default: ${DEFAULT-VALUE}).")
    Path out;

    @Option(names = "--mapping", description = "Override the built-in ACT rule mapping.")
    Path mapping;

    @Option(names = "--rules", split = ",", description = "Only evaluate these ACT rule ids.")
    Set<String> onlyRules = Set.of();

    @Option(names = "--scope", defaultValue = "all", description = "Which ACT rules to evaluate: claimed, judgement or all (default: ${DEFAULT-VALUE}).")
    String scope;

    @Option(names = "--workers", defaultValue = "6", description = "Parallel browser pages (default: ${DEFAULT-VALUE}).")
    int workers;

    @Option(names = "--headed", description = "Run the browser with a visible window.")
    boolean headed;

    @Option(names = "--min-accuracy", description = "Exit with code 2 when claimed-scope accuracy is below this percentage.")
    Double minAccuracy;

    @Option(names = {"-v", "--verbose"}, description = "Print each case as it is evaluated.")
    boolean verbose;

    @Override
    public Integer call() {
        ActMapping map = mapping == null ? ActMapping.defaults() : ActMapping.load(mapping);
        List<ActCase> all = ActCorpus.load(testcases);
        List<ActCase> selected = new ArrayList<>();
        List<ActResult> results = new ArrayList<>();
        for (ActCase c : all) {
            Optional<ActMapping.Entry> entry = map.forActRule(c.ruleId());
            boolean inScope = entry.isPresent()
                    && (onlyRules.isEmpty() || onlyRules.contains(c.ruleId()))
                    && switch (scope) {
                        case "claimed" -> entry.get().claimed();
                        case "judgement" -> !entry.get().claimed();
                        default -> true;
                    };
            if (inScope) {
                selected.add(c);
            } else {
                results.add(ActResult.outOfScope(c));
            }
        }
        System.out.printf("ACT corpus: %d cases; evaluating %d (%d ACT rules mapped, %d claimed)%n",
                all.size(), selected.size(), map.entries().size(), map.claimedCount());
        if (selected.isEmpty()) {
            System.out.println("Nothing to evaluate.");
            return 0;
        }

        try (StaticFileServer server = corpus == null ? null : new StaticFileServer(corpus)) {
            String base = server == null ? null : server.base();
            ConcurrentLinkedQueue<ActCase> queue = new ConcurrentLinkedQueue<>(selected);
            ConcurrentLinkedQueue<ActResult> collected = new ConcurrentLinkedQueue<>();
            AtomicInteger done = new AtomicInteger();
            int n = Math.max(1, workers);
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                // Playwright is not thread safe: each worker needs its own instance and browser
                Thread t = new Thread(() -> {
                    try (Playwright playwright = Playwright.create()) {
                        Browser browser = Browsers.launchChromium(playwright, !headed);
                        worker(browser, base, map, queue, collected, done, selected.size());
                        browser.close();
                    }
                }, "act-worker-" + i);
                t.start();
                threads.add(t);
            }
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            results.addAll(collected);
        }

        // keep the corpus order so reports are stable
        List<String> order = all.stream().map(ActCase::testcaseId).toList();
        results.sort(java.util.Comparator.comparingInt(r -> order.indexOf(r.testCase().testcaseId())));

        ActSummary summary = new ActSummary(results, all.size());
        System.out.println();
        System.out.println(summary.renderConsole());
        summary.writeJson(out.resolve("act-report.json"));
        summary.writeMarkdown(out.resolve("act-report.md"));
        summary.writeEarl(out.resolve("act-earl.json"), "0.1.0");
        System.out.println("Reports: " + out.toAbsolutePath());

        List<ActResult> mistakes = summary.mistakes();
        if (!mistakes.isEmpty()) {
            System.out.printf("%nMistakes (%d):%n", mistakes.size());
            mistakes.forEach(r -> System.out.printf("  %s %-14s expected=%-12s got=%-12s %s%n    %s%n    %s%n",
                    r.testCase().ruleId(), r.verdict(), r.testCase().expected(), r.actual(),
                    String.join(",", r.selectors()), r.testCase().url(),
                    r.messages().isEmpty() ? "(no findings)" : r.messages().get(0)));
        }

        double accuracy = summary.claimedScope().accuracy() * 100;
        if (minAccuracy != null && accuracy + 1e-9 < minAccuracy) {
            System.err.printf("%nClaimed-scope accuracy %.1f%% is below the required %.1f%%.%n", accuracy, minAccuracy);
            return 2;
        }
        return 0;
    }

    private void worker(Browser browser, String base, ActMapping map, ConcurrentLinkedQueue<ActCase> queue,
                        ConcurrentLinkedQueue<ActResult> collected, AtomicInteger done, int total) {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 800));
        if (base != null) {
            // a mirrored corpus still links to third-party fonts and media; letting those requests run
            // makes page loads slow and flaky, and none of them affect the semantics being tested
            context.route("**/*", route -> {
                if (route.request().url().startsWith(base)) {
                    route.resume();
                } else {
                    route.abort();
                }
            });
        }
        Page page = context.newPage();
        page.setDefaultTimeout(30000);
        A11yConfig config = A11yConfig.builder()
                .targetVersion(WcagVersion.V2_2)
                .targetLevel(Level.AAA)
                .screenshots(false)
                .artifactsDir(out.resolve("artifacts"))
                .maxFocusStops(60)
                .build();
        Auditor auditor = new Auditor(new PlaywrightDriver(page), config);
        ActCase c;
        while ((c = queue.poll()) != null) {
            ActMapping.Entry entry = map.forActRule(c.ruleId()).orElseThrow();
            String url = base == null ? c.url() : base + c.path();
            List<Finding> selectedFindings = new ArrayList<>();
            List<String> messages = new ArrayList<>();
            Outcome actual;
            try {
                navigate(page, url);
                List<Rule> rules = entry.ruleIds().stream().map(Rules::pageRule).flatMap(Optional::stream).toList();
                PageAudit audit = auditor.auditState("act", rules, null);
                for (Finding f : audit.findings()) {
                    if (entry.selectors().stream().anyMatch(s -> s.matches(f))) {
                        selectedFindings.add(f);
                    }
                }
                actual = ActScorer.aggregate(selectedFindings);
                selectedFindings.stream().filter(f -> f.outcome().isIssue() || f.outcome() == Outcome.CANT_TELL)
                        .limit(3).forEach(f -> messages.add(f.message()));
                if (messages.isEmpty()) {
                    selectedFindings.stream().limit(1).forEach(f -> messages.add(f.message()));
                }
            } catch (RuntimeException e) {
                actual = Outcome.CANT_TELL;
                messages.add("error: " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            }
            ActVerdict verdict = ActScorer.verdict(c.expected(), actual);
            collected.add(new ActResult(c, entry.claimed(), actual, verdict,
                    entry.selectors().stream().map(RuleSelector::toString).toList(), messages));
            int d = done.incrementAndGet();
            if (verbose) {
                System.out.printf("  [%d/%d] %s %s expected=%s got=%s -> %s%n", d, total, c.ruleId(),
                        c.testcaseId().substring(0, 8), c.expected(), actual, verdict);
            } else if (d % 100 == 0) {
                System.out.printf("  ... %d/%d%n", d, total);
            }
        }
        context.close();
    }

    /**
     * Navigates with one retry. A benchmark run loads hundreds of pages through one browser, and a
     * transient navigation timeout must not be recorded as the rule abstaining.
     */
    private static void navigate(Page page, String url) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(30000)
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
                page.waitForTimeout(60); // let inline scripts that build the DOM settle
                return;
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last;
    }
}
