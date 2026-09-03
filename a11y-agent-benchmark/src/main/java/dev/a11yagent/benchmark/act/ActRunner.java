package dev.a11yagent.benchmark.act;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import dev.a11yagent.core.Auditor;
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.PageAudit;
import dev.a11yagent.playwright.Browsers;
import dev.a11yagent.playwright.PlaywrightDriver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Runs the claimed ACT rules against the corpus and produces one {@link ActResult} per test case. */
public final class ActRunner {

    private final ActCorpus corpus;
    private final A11yConfig config;
    private final boolean headless;
    private Consumer<String> progress = s -> { };

    public ActRunner(ActCorpus corpus, Path artifactsDir, boolean headless) {
        this.corpus = corpus;
        this.headless = headless;
        this.config = A11yConfig.builder()
                .artifactsDir(artifactsDir)
                .screenshots(false)          // the benchmark scores outcomes, not evidence
                .maxFocusStops(60)
                .build();
    }

    public ActRunner onProgress(Consumer<String> listener) {
        this.progress = listener;
        return this;
    }

    /** Compact progress: one dot per case, newline every 50. */
    public static final class DotProgress implements Consumer<String> {
        private int n;

        @Override
        public void accept(String s) {
            System.out.print('.');
            if (++n % 50 == 0) {
                System.out.println(" " + n);
            }
            System.out.flush();
        }
    }

    public List<ActResult> run(List<ActTestCase> cases, Set<String> onlyRules) {
        List<ActTestCase> scoped = cases.stream()
                .filter(c -> ActMapping.claimed(c.ruleId()))
                .filter(c -> onlyRules.isEmpty() || onlyRules.contains(c.ruleId()))
                .toList();
        List<ActResult> results = new ArrayList<>(scoped.size());
        corpus.serve();
        try (Playwright pw = Playwright.create()) {
            Browser browser = Browsers.launchChromium(pw, headless);
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 800));
            Page page = context.newPage();
            page.setDefaultTimeout(15000);
            int i = 0;
            for (ActTestCase c : scoped) {
                i++;
                progress.accept("[" + i + "/" + scoped.size() + "] " + c.label() + " expects " + c.expected());
                results.add(evaluate(page, c));
            }
            context.close();
            browser.close();
        }
        return results;
    }

    private ActResult evaluate(Page page, ActTestCase c) {
        Set<String> ruleIds = ActMapping.rulesFor(c.ruleId());
        try {
            page.navigate(corpus.urlFor(c));
            page.waitForLoadState();
            page.waitForTimeout(60);
        } catch (RuntimeException e) {
            return new ActResult(c, Outcome.CANT_TELL, List.of("navigation failed: " + e.getMessage()), List.copyOf(ruleIds));
        }
        Auditor auditor = new Auditor(new PlaywrightDriver(page), config);
        PageAudit audit;
        try {
            audit = auditor.checkRules(ruleIds, c.ruleId());
        } catch (RuntimeException e) {
            return new ActResult(c, Outcome.CANT_TELL, List.of("rule execution failed: " + e.getMessage()), List.copyOf(ruleIds));
        }
        return new ActResult(c, aggregate(audit.findings()), messages(audit.findings()), List.copyOf(ruleIds));
    }

    /**
     * Aggregates the findings of the mapped rules into a single outcome for the test case, mirroring how
     * a tool reports on a page: any failure dominates, then anything inconclusive, then a pass, and
     * finally inapplicable when no rule found a target.
     */
    static Outcome aggregate(List<Finding> findings) {
        boolean failed = false;
        boolean inconclusive = false;
        boolean passed = false;
        for (Finding f : findings) {
            switch (f.outcome()) {
                case FAILED -> failed = true;
                case NEEDS_REVIEW, CANT_TELL -> inconclusive = true;
                case PASSED -> passed = true;
                case INAPPLICABLE -> { }
            }
        }
        if (failed) {
            return Outcome.FAILED;
        }
        if (inconclusive) {
            return Outcome.CANT_TELL;
        }
        return passed ? Outcome.PASSED : Outcome.INAPPLICABLE;
    }

    private static List<String> messages(List<Finding> findings) {
        return findings.stream()
                .filter(f -> f.outcome() == Outcome.FAILED || f.outcome() == Outcome.NEEDS_REVIEW || f.outcome() == Outcome.CANT_TELL)
                .map(f -> f.ruleId() + ": " + f.message() + " @" + f.target().selector())
                .limit(4)
                .toList();
    }
}
