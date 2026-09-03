package dev.a11yagent.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "a11y-agent", mixinStandardHelpOptions = true, version = "a11y-agent 0.1.0",
        description = "Accessibility test agent: WCAG 2.0/2.1/2.2 (A/AA/AAA) audits with Playwright, vision-model judgements and VPAT generation.",
        subcommands = {AuditCommand.class, CheckCommand.class, JourneyCommand.class, VpatCommand.class, RulesCommand.class})
public final class Main implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int code = new CommandLine(new Main()).execute(args);
        System.exit(code);
    }
}
