package dev.a11yagent.benchmark;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "a11y-benchmark", mixinStandardHelpOptions = true, version = "a11y-agent 0.1.0",
        description = "Run a11y-agent against public accessibility benchmarks (W3C ACT Rules).",
        subcommands = {ActCommand.class})
public final class Main implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        // `java -jar a11y-benchmark.jar` with no args runs the ACT suite
        String[] argv = args.length == 0 ? new String[]{"act"} : args;
        int code = new CommandLine(new Main()).execute(argv);
        System.exit(code);
    }
}
