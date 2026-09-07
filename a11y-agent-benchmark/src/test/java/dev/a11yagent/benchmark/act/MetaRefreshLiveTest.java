package dev.a11yagent.benchmark.act;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import dev.a11yagent.core.Auditor;
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.playwright.Browsers;
import dev.a11yagent.playwright.PlaywrightDriver;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MetaRefreshLiveTest {

    @Test
    void delayedRefreshIsFailedWhenThePragmaWouldNavigate() {
        Path cache = Path.of("target/act-corpus");
        if (!cache.resolve("testcases.json").toFile().isFile()) {
            cache = Path.of("a11y-agent-benchmark/target/act-corpus");
        }
        try (ActCorpus corpus = new ActCorpus(cache); Playwright pw = Playwright.create()) {
            corpus.fetch(false);
            String base = corpus.serve();
            String url = base
                    + "/WAI/content-assets/wcag-act-rules/testcases/bc659a/56857820788db21498e95a5cbba65d59a9a2b892.html";
            Browser browser = Browsers.launchChromium(pw, true);
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 800));
            context.route("**/*", route -> {
                if (route.request().url().startsWith(base)) {
                    route.resume();
                } else {
                    route.abort();
                }
            });
            Page page = context.newPage();
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            page.waitForTimeout(60);
            Auditor auditor = new Auditor(new PlaywrightDriver(page), A11yConfig.builder().screenshots(false).recordVideo(false).build());
            List<Finding> findings = ActMapping.select("bc659a",
                    auditor.checkRules(Set.of("timing-adjustable"), "bc659a").findings());
            String html = (String) page.evaluate("() => document.documentElement.outerHTML.slice(0, 800)");
            context.close();
            browser.close();
            assertEquals(Outcome.FAILED, ActRunner.aggregate(findings),
                    () -> "html=" + html + " findings=" + findings);
            assertTrue(findings.stream().anyMatch(f -> "meta-refresh".equals(f.evidence().data().get("check"))),
                    () -> findings.toString());
        }
    }
}
