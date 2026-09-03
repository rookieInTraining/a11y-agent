package dev.a11yagent.cli;

import dev.a11yagent.core.model.AuditReport;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "check", description = "Run one rule (e.g. focus-visible) or every rule for one success criterion (e.g. 2.4.7) against a URL.", mixinStandardHelpOptions = true)
final class CheckCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Rule id or WCAG success criterion id.")
    String ruleOrCriterion;

    @Parameters(index = "1", description = "URL to check.")
    String url;

    @Mixin
    CommonOptions opts;

    @Override
    public Integer call() {
        try (BrowserSession s = new BrowserSession(opts)) {
            s.page.navigate(url);
            s.page.waitForLoadState();
            AuditReport report = s.agent.check(ruleOrCriterion);
            return opts.finish(report);
        }
    }
}
