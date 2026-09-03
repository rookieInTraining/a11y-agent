package dev.a11yagent.cli;

import dev.a11yagent.playwright.PlaywrightDriver;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "axtree", description = "Print the browser accessibility tree of a URL (roles, names, states) — what a screen reader receives.", mixinStandardHelpOptions = true)
final class AxTreeCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "URL to inspect.")
    String url;

    @Option(names = "--depth", defaultValue = "40", description = "Maximum depth to print (default: ${DEFAULT-VALUE}).")
    int depth;

    @Mixin
    CommonOptions opts;

    @Override
    public Integer call() {
        try (BrowserSession s = new BrowserSession(opts)) {
            s.page.navigate(url);
            s.page.waitForLoadState();
            System.out.print(new PlaywrightDriver(s.page).renderAccessibilityTree(depth));
            return 0;
        }
    }
}
