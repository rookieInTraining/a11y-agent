package dev.a11yagent.benchmark.act;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Runs the claimed ACT rules against the corpus and produces one {@link ActResult} per test case. */
public final class ActRunner {

    private final ActCorpus corpus;
    private final A11yConfig config;
    private final boolean headless;
    private final int workers;
    private Consumer<String> progress = s -> { };

    public ActRunner(ActCorpus corpus, Path artifactsDir, boolean headless) {
        this(corpus, artifactsDir, headless, 6);
    }

    public ActRunner(ActCorpus corpus, Path artifactsDir, boolean headless, int workers) {
        this.corpus = corpus;
        this.headless = headless;
        this.workers = Math.max(1, workers);
        this.config = A11yConfig.builder()
                .artifactsDir(artifactsDir)
                .screenshots(false)          // the benchmark scores outcomes, not evidence
                .recordVideo(false)
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
        ConcurrentLinkedQueue<ActTestCase> queue = new ConcurrentLinkedQueue<>(scoped);
        ConcurrentLinkedQueue<ActResult> collected = new ConcurrentLinkedQueue<>();
        AtomicInteger done = new AtomicInteger();
        corpus.serve();
        String base = corpus.baseUrl();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            Thread t = new Thread(() -> {
                try (Playwright pw = Playwright.create()) {
                    Browser browser = Browsers.launchChromium(pw, headless);
                    BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 800));
                    if (base != null) {
                        context.route("**/*", route -> {
                            if (route.request().url().startsWith(base)) {
                                route.resume();
                            } else {
                                route.abort();
                            }
                        });
                    }
                    Page page = context.newPage();
                    page.setDefaultTimeout(15000);
                    Auditor auditor = new Auditor(new PlaywrightDriver(page), config);
                    ActTestCase c;
                    while ((c = queue.poll()) != null) {
                        collected.add(evaluate(page, auditor, c));
                        int n = done.incrementAndGet();
                        progress.accept("[" + n + "/" + scoped.size() + "] " + c.label() + " expects " + c.expected());
                    }
                    context.close();
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
        // keep corpus order so reports are stable
        List<String> order = scoped.stream().map(ActTestCase::testcaseId).toList();
        List<ActResult> results = new ArrayList<>(collected);
        results.sort(java.util.Comparator.comparingInt(r -> order.indexOf(r.testCase().testcaseId())));
        return results;
    }

    private ActResult evaluate(Page page, Auditor auditor, ActTestCase c) {
        Set<String> ruleIds = ActMapping.rulesFor(c.ruleId());
        try {
            int status = navigate(page, corpus.urlFor(c));
            if (status >= 400) {
                return new ActResult(c, Outcome.CANT_TELL, List.of("navigation failed: HTTP " + status), List.copyOf(ruleIds));
            }
        } catch (RuntimeException e) {
            return new ActResult(c, Outcome.CANT_TELL, List.of("navigation failed: " + e.getMessage()), List.copyOf(ruleIds));
        }
        PageAudit audit;
        try {
            audit = auditor.checkRules(ruleIds, c.ruleId());
        } catch (RuntimeException e) {
            return new ActResult(c, Outcome.CANT_TELL, List.of("rule execution failed: " + e.getMessage()), List.copyOf(ruleIds));
        }
        List<Finding> selected = ActMapping.select(c.ruleId(), audit.findings());
        return new ActResult(c, aggregate(selected), messages(selected), List.copyOf(ruleIds));
    }

    /** @return HTTP status of the document request */
    private static int navigate(Page page, String url) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                var resp = page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(30000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.waitForTimeout(60);
                return resp == null ? 200 : resp.status();
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last;
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
