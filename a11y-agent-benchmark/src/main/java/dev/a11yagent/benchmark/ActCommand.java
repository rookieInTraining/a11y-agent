package dev.a11yagent.benchmark;

import dev.a11yagent.benchmark.act.ActCorpus;
import dev.a11yagent.benchmark.act.ActMapping;
import dev.a11yagent.benchmark.act.ActReport;
import dev.a11yagent.benchmark.act.ActResult;
import dev.a11yagent.benchmark.act.ActRunner;
import dev.a11yagent.benchmark.act.ActTestCase;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "act", description = "Run the W3C ACT Rules test cases against a11y-agent and score accuracy per rule.", mixinStandardHelpOptions = true)
public final class ActCommand implements Callable<Integer> {

    @Option(names = "--cache", defaultValue = "target/act-corpus", description = "Local corpus cache directory (default: ${DEFAULT-VALUE}).")
    Path cache;

    @Option(names = "--out", defaultValue = "target/benchmark", description = "Where to write the report (default: ${DEFAULT-VALUE}).")
    Path out;

    @Option(names = "--refresh", description = "Re-download the corpus even if cached.")
    boolean refresh;

    @Option(names = "--rule", split = ",", description = "Only evaluate these ACT rule ids (for triage).")
    Set<String> rules = Set.of();

    @Option(names = "--headed", description = "Show the browser window.")
    boolean headed;

    @Option(names = {"-v", "--verbose"}, description = "Print each test case as it runs.")
    boolean verbose;

    @Option(names = "--errors", defaultValue = "40", description = "How many incorrect results to print (default: ${DEFAULT-VALUE}).")
    int errors;

    @Option(names = "--min-accuracy", defaultValue = "0", description = "Exit non-zero when accuracy is below this percentage.")
    double minAccuracy;

    @Override
    public Integer call() {
        try (ActCorpus corpus = new ActCorpus(cache)) {
            System.out.println("Fetching ACT corpus into " + cache.toAbsolutePath() + " ...");
            List<ActTestCase> cases = corpus.fetch(refresh);
            int corpusRules = (int) cases.stream().map(ActTestCase::ruleId).distinct().count();
            System.out.printf("Corpus: %d cases, %d rules. Claimed: %d rules.%n", cases.size(), corpusRules, ActMapping.claims().size());

            ActRunner runner = new ActRunner(corpus, out.resolve("artifacts"), !headed);
            if (verbose) {
                runner.onProgress(s -> System.out.println("  " + s));
            } else {
                runner.onProgress(new ActRunner.DotProgress());
            }
            long start = System.currentTimeMillis();
            List<ActResult> results = runner.run(cases, rules);
            long elapsed = System.currentTimeMillis() - start;

            ActReport report = new ActReport(results, cases.size(), corpusRules);
            System.out.println();
            System.out.println(report.summary());
            System.out.println(report.errorDetail(errors));
            System.out.printf("Ran %d cases in %.1fs%n", results.size(), elapsed / 1000.0);
            report.write(out);
            System.out.println("Report: " + out.resolve("act-summary.txt").toAbsolutePath());

            String uncovered = cases.stream().map(ActTestCase::ruleId).distinct()
                    .filter(id -> !ActMapping.claimed(id) && !ActMapping.NOT_IMPLEMENTED.containsKey(id))
                    .collect(Collectors.joining(", "));
            if (!uncovered.isEmpty()) {
                System.out.println("ACT rules neither claimed nor documented as out of scope: " + uncovered);
            }
            return report.accuracy() * 100 < minAccuracy ? 1 : 0;
        }
    }
}
