package dev.a11yagent.playwright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.report.HtmlReportWriter;
import dev.a11yagent.core.report.ReportJson;
import dev.a11yagent.core.vpat.Conformance;
import dev.a11yagent.core.vpat.VpatDocument;
import dev.a11yagent.core.vpat.VpatEntry;
import dev.a11yagent.core.vpat.VpatGenerator;
import dev.a11yagent.core.vpat.VpatOptions;
import dev.a11yagent.core.wcag.Wcag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JourneyTest extends BrowserTestBase {

    private AuditReport runCheckout() {
        return agent().journey("checkout")
                .start(server.url("/journey/step1.html"))
                .step("fill details", p -> {
                    p.fill("#email", "jane@example.com");
                    p.fill("#given", "Jane");
                    p.click("#next");
                })
                .step("delivery", p -> p.click("#next"))
                .run();
    }

    @Test
    void crossStepRulesDetectInconsistencies() {
        AuditReport r = runCheckout();
        assertEquals(3, r.pages().size());
        assertEquals(List.of("start", "fill details", "delivery"), r.pages().stream().map(p -> p.step()).toList());

        List<Finding> nav = findings(r, "consistent-navigation", Outcome.FAILED);
        assertEquals(1, nav.size(), () -> r.journeyFindings().toString());
        assertTrue(nav.get(0).message().contains("different order"));

        List<Finding> ident = findings(r, "consistent-identification", Outcome.FAILED);
        assertTrue(ident.stream().anyMatch(f -> f.message().contains("step3.html") && f.message().contains("review order") && f.message().contains("next step")), () -> ident.toString());

        List<Finding> help = findings(r, "consistent-help", Outcome.FAILED);
        assertEquals(1, help.size(), () -> r.journeyFindings().toString());
        assertTrue(help.get(0).message().contains("header") && help.get(0).message().contains("footer"));

        List<Finding> redundant = findings(r, "redundant-entry", Outcome.NEEDS_REVIEW);
        assertEquals(1, redundant.size(), () -> r.journeyFindings().toString());
        assertTrue(redundant.get(0).message().contains("email"));
    }

    @Test
    void reportsRoundTripAndVpatIsConservative() throws Exception {
        AuditReport r = runCheckout();
        Path json = artifacts.resolve("journey-report.json");
        ReportJson.write(r, json);
        AuditReport back = ReportJson.read(json);
        assertEquals(r.allFindings().size(), back.allFindings().size());
        assertEquals(r.pages().get(1).url(), back.pages().get(1).url());
        assertEquals(r.targetLevel(), back.targetLevel());

        Path html = artifacts.resolve("journey-report.html");
        HtmlReportWriter.write(r, html);
        String doc = Files.readString(html);
        assertTrue(doc.contains("consistent-navigation"));
        assertTrue(doc.contains("<html lang=\"en\">"));

        VpatDocument vpat = VpatGenerator.generate(back, VpatOptions.forProduct("Fixture shop 1.0"));
        VpatEntry nav = vpat.entries().stream().filter(e -> e.criterion().equals(Wcag.get("3.2.3"))).findFirst().orElseThrow();
        assertEquals(Conformance.DOES_NOT_SUPPORT, nav.conformance());
        VpatEntry redundant = vpat.entries().stream().filter(e -> e.criterion().equals(Wcag.get("3.3.7"))).findFirst().orElseThrow();
        assertEquals(Conformance.NOT_EVALUATED, redundant.conformance(), "review-only evidence must not become a conformance claim");
        VpatEntry captions = vpat.entries().stream().filter(e -> e.criterion().equals(Wcag.get("1.2.2"))).findFirst().orElseThrow();
        assertEquals(Conformance.NOT_EVALUATED, captions.conformance());
        assertFalse(captions.automated());
        assertEquals(86, vpat.entries().size(), "WCAG 2.2 has 86 success criteria (4.1.1 removed)");

        String vpatHtml = vpat.toHtml();
        assertTrue(vpatHtml.contains("Accessibility Conformance Report"));
        assertTrue(vpat.toMarkdown().contains("| 3.2.3 Consistent Navigation (Level AA) | Does Not Support |"));
        assertTrue(vpat.toJson().contains("\"template\" : \"VPAT 2.5 WCAG\""));
    }
}
