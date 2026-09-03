package dev.a11yagent.cli;

import dev.a11yagent.core.model.AuditReport;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "audit", description = "Audit a single URL with every rule in scope.", mixinStandardHelpOptions = true)
final class AuditCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "URL to audit.")
    String url;

    @Mixin
    CommonOptions opts;

    @Override
    public Integer call() {
        try (BrowserSession s = new BrowserSession(opts)) {
            s.page.navigate(url);
            s.page.waitForLoadState();
            AuditReport report = s.agent.audit(url);
            return opts.finish(report);
        }
    }
}
