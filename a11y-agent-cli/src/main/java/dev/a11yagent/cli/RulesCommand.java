package dev.a11yagent.cli;

import dev.a11yagent.core.rules.Rules;
import dev.a11yagent.core.wcag.Criterion;
import dev.a11yagent.core.wcag.Level;
import dev.a11yagent.core.wcag.Wcag;
import dev.a11yagent.core.wcag.WcagVersion;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "rules", description = "List the rule catalogue and WCAG coverage.", mixinStandardHelpOptions = true)
final class RulesCommand implements Callable<Integer> {

    @Option(names = "--coverage", description = "Print every WCAG 2.2 criterion with the rules covering it.")
    boolean coverage;

    @Override
    public Integer call() {
        System.out.println("Page rules:");
        Rules.pageRules().forEach(r -> System.out.printf("  %-36s %-8s %-14s %s%n", r.id(), r.kind(),
                r.criteria().stream().map(Criterion::id).sorted().collect(Collectors.joining(",")), r.description()));
        System.out.println("Journey rules:");
        Rules.journeyRules().forEach(r -> System.out.printf("  %-36s %-8s %-14s %s%n", r.id(), "CROSS",
                r.criteria().stream().map(Criterion::id).sorted().collect(Collectors.joining(",")), r.description()));
        if (coverage) {
            System.out.println();
            System.out.println("WCAG 2.2 coverage:");
            for (Criterion c : Wcag.forConformance(WcagVersion.V2_2, Level.AAA)) {
                String rules = Rules.pageRulesFor(c).stream().map(r -> r.id()).collect(Collectors.joining(", "));
                String jr = Rules.journeyRulesFor(c).stream().map(r -> r.id()).collect(Collectors.joining(", "));
                String all = (rules + (rules.isEmpty() || jr.isEmpty() ? "" : ", ") + jr);
                System.out.printf("  %-7s %-4s %-48s %s%n", c.id(), c.levelIn(WcagVersion.V2_2), c.name(), all.isEmpty() ? "-" : all);
            }
        }
        return 0;
    }
}
