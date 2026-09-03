package dev.a11yagent.cli;

import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.report.ReportJson;
import dev.a11yagent.core.vpat.VpatDocument;
import dev.a11yagent.core.vpat.VpatGenerator;
import dev.a11yagent.core.vpat.VpatOptions;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "vpat", description = "Generate a VPAT 2.5 (WCAG edition) conformance report from one or more report.json files.", mixinStandardHelpOptions = true)
final class VpatCommand implements Callable<Integer> {

    @Parameters(arity = "1..*", description = "report.json files produced by audit/check/journey.")
    List<Path> reports;

    @Option(names = "--product", required = true, description = "Product name and version.")
    String product;

    @Option(names = "--description", defaultValue = "", description = "Product description.")
    String description;

    @Option(names = "--vendor", defaultValue = "", description = "Vendor / team.")
    String vendor;

    @Option(names = "--evaluator", defaultValue = "a11y-agent (automated) — pending human review", description = "Evaluator.")
    String evaluator;

    @Option(names = "--contact", defaultValue = "", description = "Contact information.")
    String contact;

    @Option(names = "--notes", defaultValue = "", description = "Notes.")
    String notes;

    @Option(names = {"-o", "--out"}, defaultValue = "a11y-artifacts", description = "Output directory (default: ${DEFAULT-VALUE}).")
    Path out;

    @Override
    public Integer call() {
        List<AuditReport> loaded = reports.stream().map(ReportJson::read).toList();
        VpatOptions base = VpatOptions.forProduct(product);
        VpatOptions options = new VpatOptions(product, description, vendor, evaluator, contact, notes, base.evaluationMethods(), LocalDate.now());
        VpatDocument doc = VpatGenerator.generate(loaded, options);
        doc.write(out.resolve("vpat.html"), out.resolve("vpat.md"), out.resolve("vpat.json"));
        doc.summary().forEach((c, n) -> System.out.printf("  %-20s %d%n", c.label(), n));
        System.out.println("VPAT: " + out.resolve("vpat.html").toAbsolutePath());
        return 0;
    }
}
