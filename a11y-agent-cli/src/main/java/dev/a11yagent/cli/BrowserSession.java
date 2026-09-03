package dev.a11yagent.cli;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import dev.a11yagent.playwright.A11yAgent;
import dev.a11yagent.playwright.Browsers;

/** Owns Playwright lifecycle for one CLI invocation. */
final class BrowserSession implements AutoCloseable {

    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    final Page page;
    final A11yAgent agent;

    BrowserSession(CommonOptions opts) {
        this.playwright = Playwright.create();
        this.browser = Browsers.launchChromium(playwright, !opts.headed);
        int[] vp = opts.viewportSize();
        this.context = browser.newContext(new Browser.NewContextOptions().setViewportSize(vp[0], vp[1]));
        this.page = context.newPage();
        this.agent = A11yAgent.forPage(page, opts.config());
        if (opts.verbose) {
            agent.onProgress(s -> System.err.println("  … " + s));
        }
    }

    @Override
    public void close() {
        context.close();
        browser.close();
        playwright.close();
    }
}
