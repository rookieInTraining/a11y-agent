package dev.a11yagent.playwright;

import com.microsoft.playwright.Page;
import dev.a11yagent.core.Auditor;
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.journey.Journey;
import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.report.HtmlReportWriter;
import dev.a11yagent.core.report.ReportJson;
import dev.a11yagent.core.vpat.VpatDocument;
import dev.a11yagent.core.vpat.VpatGenerator;
import dev.a11yagent.core.vpat.VpatOptions;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Entry point for Playwright users, in the spirit of {@code new Alumni(driver)}:
 *
 * <pre>{@code
 * A11yAgent agent = A11yAgent.forPage(page);
 * AuditReport report = agent.audit();                 // whole page, every rule in scope
 * AuditReport focus  = agent.check("2.4.7");          // one criterion (or a rule id)
 * AuditReport flow   = agent.journey("checkout")
 *         .start("https://shop.example")
 *         .step("open cart", p -> p.click("text=Cart"))
 *         .step("checkout", p -> p.click("text=Checkout"))
 *         .run();
 * agent.write(flow, Path.of("a11y-artifacts"));      // report.json + report.html
 * agent.vpat(flow, VpatOptions.forProduct("Shop 1.0")).write(...);
 * }</pre>
 */
public final class A11yAgent {

    private final Page page;
    private final PlaywrightDriver driver;
    private final Auditor auditor;
    private final A11yConfig config;

    private A11yAgent(Page page, A11yConfig config) {
        this.page = page;
        this.config = config;
        this.driver = new PlaywrightDriver(page);
        this.auditor = new Auditor(driver, config);
    }

    /** The driver wrapping the page; useful to build several {@link Auditor}s sharing one DevTools session. */
    public PlaywrightDriver driver() {
        return driver;
    }

    public static A11yAgent forPage(Page page) {
        return new A11yAgent(page, A11yConfig.defaults());
    }

    public static A11yAgent forPage(Page page, A11yConfig config) {
        return new A11yAgent(page, config);
    }

    public A11yAgent onProgress(Consumer<String> listener) {
        auditor.onProgress(listener);
        return this;
    }

    public A11yConfig config() {
        return config;
    }

    public Auditor auditor() {
        return auditor;
    }

    /** The browser accessibility tree of the current page (Chromium), for custom screen-reader-oriented assertions. */
    public java.util.Optional<dev.a11yagent.core.ax.AxTree> accessibilityTree() {
        return driver.accessibilityTree();
    }

    /** Text rendering of the exposed accessibility tree: roles, names and states. */
    public String accessibilityTreeText() {
        return driver.renderAccessibilityTree(40);
    }

    /** Audits the page currently loaded in the Playwright {@link Page}. */
    public AuditReport audit() {
        return auditor.auditPage();
    }

    public AuditReport audit(String name) {
        return auditor.auditPage(name);
    }

    /** Runs a single rule ({@code focus-visible}) or every rule mapped to a criterion ({@code 2.4.7}). */
    public AuditReport check(String ruleOrCriterion) {
        return auditor.check(ruleOrCriterion);
    }

    public JourneyBuilder journey(String name) {
        return new JourneyBuilder(name);
    }

    public AuditReport run(Journey journey) {
        return auditor.runJourney(journey);
    }

    /** Writes {@code report.json} and {@code report.html} into {@code dir} (screenshots already live there). */
    public void write(AuditReport report, Path dir) {
        ReportJson.write(report, dir.resolve("report.json"));
        HtmlReportWriter.write(report, dir.resolve("report.html"));
    }

    public VpatDocument vpat(AuditReport report, VpatOptions options) {
        return VpatGenerator.generate(report, options);
    }

    /** Fluent journey builder whose step actions receive the Playwright {@link Page}. */
    public final class JourneyBuilder {
        private final Journey.Builder builder;

        private JourneyBuilder(String name) {
            this.builder = Journey.builder(name);
        }

        public JourneyBuilder start(String url) {
            builder.start(url);
            return this;
        }

        public JourneyBuilder step(String name, Consumer<Page> action) {
            builder.step(name, () -> {
                action.accept(page);
                page.waitForLoadState();
            });
            return this;
        }

        public Journey build() {
            return builder.build();
        }

        public AuditReport run() {
            return auditor.runJourney(build());
        }
    }
}
