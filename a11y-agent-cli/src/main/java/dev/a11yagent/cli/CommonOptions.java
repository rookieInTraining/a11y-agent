package dev.a11yagent.cli;

import dev.a11yagent.core.ai.ModelClients;
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.report.HtmlReportWriter;
import dev.a11yagent.core.report.ReportJson;
import dev.a11yagent.core.wcag.Level;
import dev.a11yagent.core.wcag.WcagVersion;
import java.nio.file.Path;
import java.util.Set;
import picocli.CommandLine.Option;

/** Options shared by the browser-driving commands. */
class CommonOptions {

    @Option(names = {"-o", "--out"}, description = "Artifacts directory (default: ${DEFAULT-VALUE}).", defaultValue = "a11y-artifacts")
    Path out;

    @Option(names = "--wcag", description = "Target WCAG version: 2.0, 2.1 or 2.2 (default: ${DEFAULT-VALUE}).", defaultValue = "2.2")
    String wcag;

    @Option(names = "--level", description = "Target level: A, AA or AAA (default: ${DEFAULT-VALUE}).", defaultValue = "AAA")
    Level level;

    @Option(names = "--ai", description = "Enable AI/vision judgements using A11Y_AI_PROVIDER / API key environment variables.")
    boolean ai;

    @Option(names = "--no-screenshots", description = "Do not capture evidence screenshots.")
    boolean noScreenshots;

    @Option(names = "--headed", description = "Run the browser with a visible window.")
    boolean headed;

    @Option(names = "--viewport", description = "Viewport WxH (default: ${DEFAULT-VALUE}).", defaultValue = "1280x800")
    String viewport;

    @Option(names = "--include", split = ",", description = "Only run these rule ids.")
    Set<String> include = Set.of();

    @Option(names = "--exclude", split = ",", description = "Skip these rule ids.")
    Set<String> exclude = Set.of();

    @Option(names = "--max-focus-stops", description = "Maximum Tab presses for keyboard probes (default: ${DEFAULT-VALUE}).", defaultValue = "150")
    int maxFocusStops;

    @Option(names = "--fail-on", description = "Exit code 2 when findings with this outcome exist: FAILED (default) or NEEDS_REVIEW.", defaultValue = "FAILED")
    Outcome failOn;

    @Option(names = {"-v", "--verbose"}, description = "Print progress.")
    boolean verbose;

    A11yConfig config() {
        A11yConfig.Builder b = A11yConfig.builder()
                .targetVersion(WcagVersion.parse(wcag))
                .targetLevel(level)
                .artifactsDir(out)
                .screenshots(!noScreenshots)
                .maxFocusStops(maxFocusStops)
                .includeRules(include)
                .excludeRules(exclude);
        if (ai) {
            b.modelClient(ModelClients.fromEnv().orElseThrow(() -> new IllegalStateException(
                    "--ai requested but no model configured. Set ANTHROPIC_API_KEY or OPENAI_API_KEY, or A11Y_AI_PROVIDER=ollama.")));
        }
        return b.build();
    }

    int[] viewportSize() {
        String[] p = viewport.toLowerCase().split("x");
        return new int[] {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
    }

    int finish(AuditReport report) {
        ReportJson.write(report, out.resolve("report.json"));
        HtmlReportWriter.write(report, out.resolve("report.html"));
        System.out.printf("%n%s: %d failed, %d need review, %d can't tell, %d passed, %d inapplicable%n",
                report.name(), report.count(Outcome.FAILED), report.count(Outcome.NEEDS_REVIEW), report.count(Outcome.CANT_TELL),
                report.count(Outcome.PASSED), report.count(Outcome.INAPPLICABLE));
        report.issues().limit(40).forEach(f -> System.out.printf("  [%s] %s (%s)%s: %s%n",
                f.outcome(), f.ruleId(), f.criteria().stream().map(c -> c.id()).sorted().reduce((a, c) -> a + "," + c).orElse(""),
                f.step() != null ? " @" + f.step() : "", f.message()));
        System.out.println("Report: " + out.resolve("report.html").toAbsolutePath());
        boolean fail = report.allFindings().stream().anyMatch(f -> f.outcome() == Outcome.FAILED
                || (failOn == Outcome.NEEDS_REVIEW && f.outcome() == Outcome.NEEDS_REVIEW));
        return fail ? 2 : 0;
    }
}
