package dev.a11yagent.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

abstract class BrowserTestBase {

    static Playwright playwright;
    static Browser browser;
    static FixtureServer server;
    static Path artifacts;

    BrowserContext context;
    Page page;

    @BeforeAll
    static void launch() throws IOException {
        playwright = Playwright.create();
        browser = Browsers.launchChromium(playwright, true);
        server = new FixtureServer();
        artifacts = Files.createTempDirectory("a11y-agent-test");
    }

    @AfterAll
    static void shutdown() {
        server.close();
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 800));
        page = context.newPage();
    }

    @AfterEach
    void closePage() {
        context.close();
    }

    A11yConfig config() {
        return A11yConfig.builder().artifactsDir(artifacts).build();
    }

    A11yAgent agent() {
        return A11yAgent.forPage(page, config());
    }

    static List<Finding> findings(AuditReport r, String ruleId, Outcome outcome) {
        return r.allFindings().stream().filter(f -> f.ruleId().equals(ruleId) && f.outcome() == outcome).toList();
    }

    static List<Finding> findings(AuditReport r, String ruleId) {
        return r.allFindings().stream().filter(f -> f.ruleId().equals(ruleId)).toList();
    }
}
