# a11y-agent

An accessibility test agent for Java that drives a real browser through **Playwright** to evaluate
web pages and scripted user journeys against **WCAG 2.0, 2.1 and 2.2 (levels A, AA and AAA)**,
uses **vision/language models** as judges for criteria that need human-like judgement, and generates
**VPAT 2.5 / ACR** drafts from the evidence.

It is not another axe-core. The rule catalogue deliberately skips what DOM linters already do well
(missing `alt`, missing labels, contrast of solid text, duplicate ids, ARIA attribute validity) and
concentrates on the criteria those tools leave to manual testing:

| Category | What the agent actually does |
|---|---|
| **Runtime probes** | Presses <kbd>Tab</kbd> through the page and compares focused/unfocused computed styles (2.4.7), checks the visual reading order (2.4.3), samples `elementFromPoint` to detect sticky headers/banners covering focus (2.4.11/2.4.12), detects keyboard traps (2.1.2), resizes to 320 px (1.4.10), injects WCAG text-spacing overrides (1.4.12) and 200 % zoom (1.4.4) and diffs clipping/overlap. |
| **In-page heuristics** | Alt-text *quality* (file names, generic words, redundancy), link purpose in context and link-only (2.4.4/2.4.9), colour-only links (1.4.1), sensory-characteristic instructions (1.3.3), target size with spacing/inline exceptions (2.5.8/2.5.5), label-in-name (2.5.3), auto-refresh (2.2.1), moving content and infinite animations (2.2.2), non-focusable click handlers (2.1.1), autocomplete tokens for personal data (1.3.5), placeholder headings/labels (2.4.6), orientation locks (1.3.4), dragging (2.5.7), CAPTCHAs / paste blocking on login forms (3.3.8/3.3.9), `title` tooltips (1.4.13). |
| **Journeys** | Scripted multi-step flows. Cross-step rules compare page states: consistent navigation (3.2.3), consistent identification (3.2.4), consistent help (3.2.6) and redundant entry (3.3.7). |
| **AI judges** | A pluggable `ModelClient` (Anthropic, OpenAI, any OpenAI-compatible endpoint such as Ollama) receives screenshot crops plus DOM context and returns a structured `{result, confidence, rationale}`. Used today for 1.1.1 alt-text adequacy and ambiguous focus indicators; every AI verdict is quoted in the report with its confidence and never becomes a conformance claim without human confirmation. |
| **Reporting** | ACT-Rules outcome vocabulary (`PASSED`, `FAILED`, `INAPPLICABLE`, `CANT_TELL`, `NEEDS_REVIEW`), JSON + accessible HTML report with evidence screenshots, and a VPAT 2.5 (WCAG edition) generator producing HTML, Markdown and JSON. |

## Modules

```
a11y-agent-core         driver-agnostic: WCAG registry, rule engine, in-page JS bundle, journeys, AI judges, reports, VPAT
a11y-agent-playwright   PlaywrightDriver + A11yAgent facade (new A11yAgent(page), Alumnium-style)
a11y-agent-cli          a11y-agent audit | check | journey | vpat | rules
```

The in-page rules live in a single dependency-free JavaScript file
(`a11y-agent-core/src/main/resources/dev/a11yagent/inpage/a11y-agent.js`). It is injected through
`page.evaluate` today and is written so that the identical file can be loaded as a browser-extension
content script later. Everything that needs real user-agent behaviour (keyboard events, viewport
changes, screenshots) goes through the small `PageDriver` interface, which is what a Selenium BiDi
adapter would implement.

## Requirements

* Java 21+
* Maven 3.9+
* A Chromium: either `mvn -pl a11y-agent-playwright exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"`
  or set `A11Y_BROWSER_CHANNEL=chrome` to use an installed Google Chrome.

```bash
mvn install            # builds all modules and runs the tests (browser tests need Chromium)
mvn install -DskipTests
```

## Using the Java API

```java
try (Playwright pw = Playwright.create()) {
    Browser browser = pw.chromium().launch();
    Page page = browser.newPage();
    page.navigate("https://shop.example/");

    A11yAgent agent = A11yAgent.forPage(page);

    // 1. one check: a rule id or a success criterion id
    AuditReport focus = agent.check("2.4.7");
    assertFalse(focus.hasFailures());

    // 2. whole page, every rule in scope of the configured WCAG version/level
    AuditReport report = agent.audit();

    // 3. a scripted journey; each step is audited and cross-page rules run at the end
    AuditReport flow = agent.journey("checkout")
            .start("https://shop.example/")
            .step("open cart", p -> p.click("text=Cart"))
            .step("identify", p -> { p.fill("#email", "jane@example.com"); p.click("text=Continue"); })
            .step("address", p -> p.click("text=Continue"))
            .run();

    agent.write(flow, Path.of("a11y-artifacts"));                       // report.json + report.html
    agent.vpat(flow, VpatOptions.forProduct("Shop 1.0"))
         .write(Path.of("vpat.html"), Path.of("vpat.md"), Path.of("vpat.json"));
}
```

Configuration:

```java
A11yConfig config = A11yConfig.builder()
        .targetVersion(WcagVersion.V2_2)          // 2.0, 2.1 or 2.2 (levels move: 2.4.7 is A in 2.2)
        .targetLevel(Level.AA)                    // A, AA or AAA
        .artifactsDir(Path.of("a11y-artifacts"))
        .modelClient(ModelClients.fromEnv().orElse(null))   // enables AI judges
        .maxAiJudgements(25)
        .excludeRules(Set.of("content-on-hover-title"))
        .build();
A11yAgent agent = A11yAgent.forPage(page, config);
```

Because `A11yAgent` wraps the `Page` you already have, it slots into existing Playwright test suites
without changing how they navigate or authenticate.

## Command line

```bash
JAR=a11y-agent-cli/target/a11y-agent.jar

java -jar $JAR audit https://example.com --wcag 2.2 --level AA -o out
java -jar $JAR check focus-visible https://example.com
java -jar $JAR check 2.5.8 https://example.com --ai
java -jar $JAR journey checkout.yaml -o out-journey -v
java -jar $JAR vpat out/report.json out-journey/report.json --product "Shop 1.0" --vendor "Acme"
java -jar $JAR rules --coverage
```

Exit code is `2` when any `FAILED` finding exists (`--fail-on NEEDS_REVIEW` to be stricter), so the
CLI can gate CI.

Journey YAML:

```yaml
name: checkout
start: https://shop.example/
steps:
  - name: open cart
    actions:
      - click: "text=Cart"
  - name: identify
    actions:
      - fill: { selector: "#email", value: "jane@example.com" }
      - clickRole: { role: button, name: "Continue" }
      - waitFor: "#address"
```

Supported actions: `navigate`, `click`, `clickRole`, `fill`, `select`, `check`, `press`, `type`,
`wait` (ms), `waitFor` (selector), `evaluate`.

## AI / vision models

Set environment variables and pass `--ai` (CLI) or `.modelClient(...)` (API):

| Variable | Meaning |
|---|---|
| `A11Y_AI_PROVIDER` | `anthropic`, `openai`, `ollama` or `none` (auto-detected from the API keys when unset) |
| `A11Y_AI_MODEL` | model name; defaults are provider specific and should be pinned for reproducible reports |
| `A11Y_AI_BASE_URL` | endpoint override (Ollama, proxies, gateways) |
| `ANTHROPIC_API_KEY`, `OPENAI_API_KEY` | credentials |

Screenshot crops of the element plus a little context are sent, not whole pages. Every verdict is
stored with the model id, confidence and rationale. A `FAIL` with confidence ≥ 0.75 becomes `FAILED`;
anything less becomes `NEEDS_REVIEW`.

## VPAT generation

`VpatGenerator` maps evidence to the ITI VPAT 2.5 conformance terms conservatively:

| Evidence for a criterion | Conformance |
|---|---|
| any `FAILED`, no `PASSED` | Does Not Support |
| any `FAILED` with some `PASSED` | Partially Supports |
| only `NEEDS_REVIEW` / `CANT_TELL` | Not Evaluated (items listed for the auditor) |
| only `PASSED` (+ `INAPPLICABLE`) | Supports (rules named in the remarks) |
| only `INAPPLICABLE` | Not Applicable |
| no automated rule covers it | Not Evaluated, flagged for manual testing |

Multiple `report.json` files (several journeys, several page sets) can be merged into one ACR.
The output is a draft: Not Evaluated rows and AI-based judgements must be confirmed by a human.

## Rule catalogue and WCAG coverage

`java -jar a11y-agent.jar rules --coverage` prints the full list. Today 26 page rules and 4 journey
rules cover 30 of the 86 WCAG 2.2 success criteria, all of them criteria that are not mechanically
testable by DOM linters. Criteria without coverage are reported as *Not Evaluated* rather than
silently omitted.

## Tests

```bash
mvn test                       # core unit tests + Playwright browser tests
mvn -pl a11y-agent-core test   # no browser needed
```

Browser tests run against fixture pages under `a11y-agent-playwright/src/test/resources/fixtures`
served by an embedded HTTP server: a deliberately broken page, a clean page, a keyboard-trap page
and a three-step checkout with cross-page inconsistencies.

## Roadmap

* Selenium 4 BiDi `PageDriver` so the same rules run on existing Selenium suites.
* Browser extension (MV3) loading `a11y-agent.js` as a content script and using `chrome.debugger`
  for the runtime probes; the artifacts/report layer is already framework-agnostic.
* MCP server exposing `audit`, `check` and `journey` to coding agents.
* More AI judges: 1.4.11 non-text contrast on screenshots, 2.4.6 heading descriptiveness,
  3.1.2 language of parts, 3.3.2 instructions.
* ARIA Authoring Practices behaviour probes (tabs, menus, dialogs, comboboxes) via keyboard.
* DOCX export of the VPAT.
