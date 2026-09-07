# Getting started

This guide covers installing **a11y-agent**, plugging it into a suite that already owns a browser, and the commands used to build, test, and audit.

The agent does not take ownership of your browser. You keep launching Playwright, Selenium, or any other driver the way you do today; a11y-agent only borrows the current page for the duration of an audit.

## 1. Setup

### Prerequisites

- **Java 21+**
- **Maven 3.9+**
- A **Chromium** browser. Accessibility-tree rules use Chrome DevTools (`Accessibility.getFullAXTree`), so Chromium or Chrome is required for that layer. DOM and most runtime rules still run without it, but AX coverage will be missing.

### Clone and build

```bash
git clone <this-repo>
cd a11y-agent
```

Install Playwright’s Chromium (once per machine):

```bash
mvn -pl a11y-agent-playwright exec:java "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
```

Or skip that download and point at a browser you already have:

| Environment variable | Example | Meaning |
|---|---|---|
| `A11Y_BROWSER_CHANNEL` | `chrome` or `msedge` | Playwright channel for an installed browser |
| `A11Y_BROWSER_EXECUTABLE` | `C:\Program Files\Google\Chrome\Application\chrome.exe` | Explicit binary path (wins over channel) |

PowerShell:

```powershell
$env:A11Y_BROWSER_CHANNEL = "chrome"
```

cmd:

```bat
set A11Y_BROWSER_CHANNEL=chrome
```

Then install the modules into the local Maven repository:

```bash
mvn install
```

That compiles every module and runs the test suite (Playwright tests need Chromium). To install artifacts without running tests:

```bash
mvn install -DskipTests
```

### Use it from another project

The artifacts are `0.1.0-SNAPSHOT` and are not published to Maven Central. After `mvn install` in this repo they live in your Maven local cache (`~/.m2/repository`). Point your suite at that cache.

`a11y-agent-playwright` already depends on `a11y-agent-core`. Playwright users only need the Playwright artifact. Selenium or any other driver (you implement `PageDriver` yourself; see [below](#2-use-an-existing-browser-from-another-framework)) should depend on `a11y-agent-core`.

#### Gradle

Gradle does not read Maven local unless you add `mavenLocal()`. Use `testImplementation` if you only call the agent from tests.

Groovy (`build.gradle`):

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation 'dev.a11yagent:a11y-agent-playwright:0.1.0-SNAPSHOT'
    // Selenium / custom PageDriver:
    // testImplementation 'dev.a11yagent:a11y-agent-core:0.1.0-SNAPSHOT'
}
```

Kotlin DSL (`build.gradle.kts`):

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation("dev.a11yagent:a11y-agent-playwright:0.1.0-SNAPSHOT")
    // Selenium / custom PageDriver:
    // testImplementation("dev.a11yagent:a11y-agent-core:0.1.0-SNAPSHOT")
}
```

After you re-run `mvn install` here, refresh Gradle so it picks up the new SNAPSHOT (`./gradlew --refresh-dependencies test`, or the equivalent refresh in the IDE).

#### Maven

```xml
<dependency>
  <groupId>dev.a11yagent</groupId>
  <artifactId>a11y-agent-playwright</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```xml
<dependency>
  <groupId>dev.a11yagent</groupId>
  <artifactId>a11y-agent-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Optional: AI judges

Vision/language judgements stay off until you configure a model. That includes leftover review after the run (Can't tell / Needs review findings, plus recording frames). Set these and pass `--ai` on the CLI, or `.modelClient(...)` in Java:

| Variable | Meaning |
|---|---|
| `A11Y_AI_PROVIDER` | `anthropic`, `openai`, `ollama`, or `none` (auto-detected from API keys when unset) |
| `A11Y_AI_MODEL` | model name; pin it for reproducible reports |
| `A11Y_AI_BASE_URL` | endpoint override (Ollama, proxies, gateways) |
| `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` | credentials |

## 2. Use an existing browser from another framework

a11y-agent is split so the rules never talk to Playwright or Selenium directly:

```
your test / app
    │  already holds Page, WebDriver, BrowserContext, …
    ▼
adapter (PlaywrightDriver today; your PageDriver for anything else)
    ▼
Auditor  (a11y-agent-core — WCAG rules, journeys, reports, VPAT)
```

`A11yAgent` and `Auditor` wrap the page you already have. They do not create a `Playwright`, `Browser`, `BrowserContext`, or `WebDriver`. Cookies, storage state, login, and routing stay exactly as your framework left them.

Runtime probes press <kbd>Tab</kbd>, may resize the viewport (reflow / text spacing / zoom), and inject CSS. Run the audit at a stable point in the flow (after the page has settled, typically at the end of a step) and restore viewport size afterwards if later assertions depend on it.

### Playwright `Page` (supported)

Pass the `Page` your suite already created:

```java
import com.microsoft.playwright.Page;
import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.playwright.A11yAgent;
import java.nio.file.Path;

// `page` comes from your existing Playwright test / helper
A11yAgent agent = A11yAgent.forPage(page);

AuditReport report = agent.audit();                 // every in-scope rule
AuditReport focus  = agent.check("2.4.7");          // one criterion, or a rule id such as "focus-visible"
// Transition-dependent criteria need observe() around the action that triggers the change:
AuditReport errors = agent.observe("3.3.1", p -> p.click("button[type=submit]"));
AuditReport status = agent.observe("4.1.3", p -> p.click("button[type=submit]"));
assertFalse(report.hasFailures());

agent.write(report, Path.of("a11y-artifacts"));     // report.json + report.html (keep next to screenshots/ and audit.webm)
```

Open `a11y-artifacts/report.html` in a browser. Issue screenshots are **viewport shots with the failing element outlined** (rose overlay + caption), not tight crops. **Audit recording** embeds `audit.webm` when ffmpeg is available. With a model configured, leftover **Can't tell** / **Needs review** findings are reviewed after the probes against those frames (and the finding text); deterministic pass/fail is not changed. The report is not only pass/fail: **How this audit was produced** lists every rule as Deterministic, AI-enhanced or AI-only, and **AI judgements** quotes the model, confidence and rationale for every finding the model actually looked at (including passes). Heuristic failures on an AI-only rule stay marked Deterministic.

Video recording is on by default (`A11yConfig.recordVideo(true)`). Encode needs **ffmpeg** on `PATH`, or `A11Y_FFMPEG` pointing at the binary (Playwright’s copy under `%LOCALAPPDATA%\ms-playwright` is also probed). Without ffmpeg the HTML report is still written; the recording section is omitted. Use `.recordVideo(false)` or CLI `--no-video` to skip the extra screenshots. Post-run leftover review still runs when a model is set (text-only if there are no frames). Disable it with `.videoEnrichment(false)` or `--no-video-enrichment`.

With configuration:

```java
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.wcag.Level;
import dev.a11yagent.core.wcag.WcagVersion;

A11yConfig config = A11yConfig.builder()
        .targetVersion(WcagVersion.V2_2)
        .targetLevel(Level.AA)
        .artifactsDir(Path.of("a11y-artifacts"))
        .recordVideo(true)   // default; writes audit.webm next to report.html
        .videoEnrichment(true) // default when a model is configured; reviews leftovers after the run
        .scopeSelector("main") // optional: limit in-page rule targets to a subtree (nav/cookie chrome excluded)
        .build();
A11yAgent agent = A11yAgent.forPage(page, config);
```

Journeys reuse the same `Page`. Step lambdas receive that Playwright `Page`, so they can call `click` / `fill` the way the rest of your suite does:

```java
AuditReport flow = agent.journey("checkout")
        .start("https://shop.example/")
        .step("open cart", p -> p.click("text=Cart"))
        .step("identify", p -> {
            p.fill("#email", "jane@example.com");
            p.click("text=Continue");
        })
        .run();
```

**Snapshot vs observe.** `check()` reads the page after your test actions finish. That is enough for static DOM issues (missing alt, unlabeled fields in the DOM, contrast). Criteria that depend on **what happens when the user submits or navigates** — error identification (3.3.1) and status messages (4.1.3) — need `observe(criterion, action)` so the agent records live-region, focus and DOM changes during the action. `runJourney()` records an observation log around every step automatically. For planted-issue scoring: use `check("3.3.2")` on placeholder-only fields, `observe("3.3.1"|"4.1.3", submit)` on forms, and `runJourney()` for 3.2.6 consistent help.

### Playwright `Browser` and `BrowserContext`

There is no `A11yAgent.forContext(...)`. Keep the context; wrap a **page** from it.

```java
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

// `browser` is the one your framework already launched
BrowserContext context = browser.newContext(new Browser.NewContextOptions()
        .setViewportSize(1280, 800)
        .setStorageStatePath(Path.of("auth.json")));   // or whatever your suite already sets

Page page = context.newPage();
page.navigate("https://app.example/dashboard");

A11yAgent agent = A11yAgent.forPage(page);
AuditReport report = agent.audit();
```

If the suite already opened the page:

```java
Page page = context.pages().get(0);   // or however you look it up
A11yAgent agent = A11yAgent.forPage(page);
```

Several pages in one context each get their own agent (and their own DevTools session):

```java
A11yAgent checkout = A11yAgent.forPage(checkoutPage);
A11yAgent settings = A11yAgent.forPage(settingsPage, config);
```

The Playwright adapter attaches CDP with `page.context().newCDPSession(page)`, so it uses **your** context. Do not close the context until the audit has finished.

Lower-level, without the `A11yAgent` facade:

```java
import dev.a11yagent.core.Auditor;
import dev.a11yagent.playwright.PlaywrightDriver;

PlaywrightDriver driver = new PlaywrightDriver(page);
Auditor auditor = new Auditor(driver, config);
AuditReport report = auditor.auditPage();
```

### Selenium `WebDriver` (bring your own adapter)

There is no `a11y-agent-selenium` module yet. Core only needs a `PageDriver`. Wrap the `WebDriver` your suite already created and hand it to `Auditor`.

`PageDriver` is the whole contract:

| Method | What the rules need |
|---|---|
| `url()` / `title()` | current document |
| `evaluate(functionExpression, arg)` | run a JS **function expression** in the page (Playwright style, e.g. `"(el) => el.tagName"`) and return JSON-serialisable values |
| `screenshot(fullPage)` / `screenshotClip(rect)` | PNG bytes for evidence |
| `press(key)` | Playwright key names: `Tab`, `Shift+Tab`, `Escape` |
| `viewport()` / `setViewport(viewport)` | CSS pixel size; reflow / zoom / text-spacing rules change this |
| `navigate(url)` / `waitMillis(ms)` | journeys and timed probes |
| `accessibilityTree()` | optional; Chromium CDP `Accessibility.getFullAXTree`. Return `Optional.empty()` and AX rules degrade |

Minimal adapter around an existing `WebDriver`:

```java
import dev.a11yagent.core.Auditor;
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.driver.PageDriver;
import dev.a11yagent.core.driver.Rect;
import dev.a11yagent.core.driver.Viewport;
import dev.a11yagent.core.model.AuditReport;
import java.util.Optional;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.interactions.Actions;

public final class SeleniumDriver implements PageDriver {

    private final WebDriver driver;   // the instance your framework already holds

    public SeleniumDriver(WebDriver driver) {
        this.driver = driver;
    }

    @Override public String url() { return driver.getCurrentUrl(); }
    @Override public String title() { return driver.getTitle(); }

    @Override
    public Object evaluate(String functionExpression, Object arg) {
        String script = "return (" + functionExpression + ").apply(null, arguments);";
        JavascriptExecutor js = (JavascriptExecutor) driver;
        return arg == null ? js.executeScript(script) : js.executeScript(script, arg);
    }

    @Override
    public byte[] screenshot(boolean fullPage) {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    @Override
    public byte[] screenshotClip(Rect clip) {
        // Prefer CDP Page.captureScreenshot with a clip; cropping a full PNG also works.
        return screenshot(false);
    }

    @Override
    public void press(String key) {
        new Actions(driver).sendKeys(toSelenium(key)).perform();
    }

    @Override
    public Viewport viewport() {
        Dimension d = driver.manage().window().getSize();
        return new Viewport(d.width, d.height);
    }

    @Override
    public void setViewport(Viewport viewport) {
        driver.manage().window().setSize(new Dimension(viewport.width(), viewport.height()));
    }

    @Override
    public void navigate(String url) { driver.navigate().to(url); }

    @Override
    public void waitMillis(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override
    public Optional<dev.a11yagent.core.ax.AxTree> accessibilityTree() {
        return Optional.empty(); // optional: ChromeDriver.getDevTools() + Accessibility.getFullAXTree
    }

    private static CharSequence toSelenium(String playwrightKey) {
        return switch (playwrightKey) {
            case "Tab" -> Keys.TAB;
            case "Shift+Tab" -> Keys.chord(Keys.SHIFT, Keys.TAB);
            case "Escape" -> Keys.ESCAPE;
            default -> playwrightKey;
        };
    }
}
```

Use it next to the rest of your Selenium (or TestNG / JUnit / Cucumber) flow:

```java
import dev.a11yagent.core.Auditor;
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.report.HtmlReportWriter;
import dev.a11yagent.core.report.ReportJson;
import java.nio.file.Path;

Path out = Path.of("a11y-artifacts");
// `webDriver` is already logged in, on the page you care about
A11yConfig config = A11yConfig.builder().artifactsDir(out).build();
Auditor auditor = new Auditor(new SeleniumDriver(webDriver), config);
AuditReport report = auditor.auditPage();
ReportJson.write(report, out.resolve("report.json"));
HtmlReportWriter.write(report, out.resolve("report.html"));
assertFalse(report.hasFailures());
```

Write `report.html` **in the same directory** as `artifactsDir` so screenshot and `audit.webm` relative paths resolve. Open that HTML file in a browser: each issue figure outlines the target, and the recording plays if ffmpeg ran.

`evaluate` must accept a **function expression**, not a Selenium script body. Wrapping with `.apply(null, arguments)` is what makes the in-page bundle (`a11y-agent.js`) work unchanged.

A Selenium 4 BiDi / CDP adapter is on the roadmap so this wrapper will not be something every suite has to maintain. Until then, the sketch above is the integration point.

### Any other framework (Cypress, WebdriverIO, custom CDP, …)

Same pattern:

1. Keep the framework’s session as the source of truth.
2. Implement `dev.a11yagent.core.driver.PageDriver` against that session.
3. Call `new Auditor(pageDriver, config).auditPage()` (or `check(...)`, `runJourney(...)`).

If the framework can already produce a Playwright `Page` (for example a Java Playwright binding behind another wrapper), prefer `A11yAgent.forPage(page)` and skip a custom driver.

## 3. Commands

### This repository’s test suite

From the repo root. Playwright browser tests serve fixture pages from `a11y-agent-playwright/src/test/resources/fixtures`.

```bash
mvn test                              # core unit tests + Playwright browser tests + benchmark unit tests
mvn install -DskipTests               # install artifacts without running tests

mvn -pl a11y-agent-core test          # no browser
mvn -pl a11y-agent-playwright test    # needs Chromium
mvn -pl a11y-agent-benchmark test     # unit tests; MetaRefreshLiveTest also needs a cached ACT corpus
```

One class or method:

```bash
mvn -pl a11y-agent-playwright test -Dtest=DomAndAxRulesTest
mvn -pl a11y-agent-playwright test -Dtest=RuntimeRulesTest#focusVisiblePassesWithFocusRing
mvn -pl a11y-agent-core test -Dtest=VpatGeneratorTest,WcagTest
```

Playwright test classes: `DomAndAxRulesTest`, `InPageRulesTest`, `RuntimeRulesTest`, `JourneyTest`.

### Package the CLI and run audits

```bash
mvn -pl a11y-agent-cli -am package -DskipTests
```

The shaded jar is `a11y-agent-cli/target/a11y-agent.jar`.

PowerShell:

```powershell
$jar = "a11y-agent-cli/target/a11y-agent.jar"

java -jar $jar audit https://example.com --wcag 2.2 --level AA -o out
java -jar $jar check focus-visible https://example.com
java -jar $jar check 2.5.8 https://example.com --ai
java -jar $jar journey checkout.yaml -o out-journey -v
java -jar $jar vpat out/report.json out-journey/report.json --product "Shop 1.0" --vendor "Acme"
java -jar $jar rules --coverage
java -jar $jar axtree https://example.com --depth 20
java -jar $jar --help
```

POSIX:

```bash
JAR=a11y-agent-cli/target/a11y-agent.jar

java -jar "$JAR" audit https://example.com --wcag 2.2 --level AA -o out
```

Exit code is `2` when any `FAILED` finding exists (`--fail-on NEEDS_REVIEW` to also fail on review items), so the CLI can gate CI.

Useful flags (audit / check / journey / axtree):

| Flag | Default | Purpose |
|---|---|---|
| `-o` / `--out` | `a11y-artifacts` | report, screenshots, `audit.webm` |
| `--wcag` | `2.2` | `2.0`, `2.1`, or `2.2` |
| `--level` | `AAA` | `A`, `AA`, or `AAA` |
| `--ai` | off | enable model judges from env (including post-run leftover review) |
| `--no-video` | recording on | skip WebM (still needs ffmpeg when recording) |
| `--no-video-enrichment` | review on (with `--ai`) | skip leftover review against recording frames |
| `--scope` | (none) | limit in-page rules to a CSS subtree, e.g. `main` |
| `--headed` | headless | show the browser |
| `--viewport` | `1280x800` | `WxH` |
| `--include` / `--exclude` | all in scope | comma-separated rule ids |
| `-v` / `--verbose` | off | print each rule as it runs |

### Benchmark (W3C ACT Rules)

```bash
mvn -pl a11y-agent-benchmark -am package -DskipTests
java -jar a11y-agent-benchmark/target/a11y-benchmark.jar
java -jar a11y-agent-benchmark/target/a11y-benchmark.jar act --rule 6cfa84 --headed -v
```

With no arguments the benchmark jar runs `act`, downloads the corpus into `target/act-corpus`, and writes `target/benchmark/act-summary.txt`.
