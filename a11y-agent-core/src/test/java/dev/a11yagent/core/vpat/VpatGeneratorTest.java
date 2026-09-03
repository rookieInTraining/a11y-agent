package dev.a11yagent.core.vpat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.PageAudit;
import dev.a11yagent.core.model.Target;
import dev.a11yagent.core.wcag.Level;
import dev.a11yagent.core.wcag.Wcag;
import dev.a11yagent.core.wcag.WcagVersion;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VpatGeneratorTest {

    private static Finding f(String rule, String sc, Outcome o) {
        return new Finding(rule, Set.of(Wcag.get(sc)), o, Impact.MODERATE, "msg " + o, new Target("#x", "<x>", null),
                Evidence.deterministic("msg"), "page", "https://example.test/");
    }

    private static AuditReport report(WcagVersion v, Level l, Finding... findings) {
        return new AuditReport("t", Instant.EPOCH, Instant.EPOCH, v, l, Set.of("r"),
                List.of(new PageAudit("page", "https://example.test/", "Example", null, List.of(findings))), List.of());
    }

    private static VpatEntry entry(VpatDocument d, String sc) {
        return d.entries().stream().filter(e -> e.criterion().id().equals(sc)).findFirst().orElseThrow();
    }

    @Test
    void mapsOutcomesToConformanceTerms() {
        AuditReport r = report(WcagVersion.V2_2, Level.AAA,
                f("focus-visible", "2.4.7", Outcome.FAILED), f("focus-visible", "2.4.7", Outcome.PASSED),
                f("no-keyboard-trap", "2.1.2", Outcome.FAILED),
                f("reflow", "1.4.10", Outcome.PASSED),
                f("timing-adjustable", "2.2.1", Outcome.INAPPLICABLE),
                f("sensory-characteristics", "1.3.3", Outcome.NEEDS_REVIEW));
        VpatDocument d = VpatGenerator.generate(r, VpatOptions.forProduct("P"));
        assertEquals(Conformance.PARTIALLY_SUPPORTS, entry(d, "2.4.7").conformance());
        assertEquals(Conformance.DOES_NOT_SUPPORT, entry(d, "2.1.2").conformance());
        assertEquals(Conformance.SUPPORTS, entry(d, "1.4.10").conformance());
        assertEquals(Conformance.NOT_APPLICABLE, entry(d, "2.2.1").conformance());
        assertEquals(Conformance.NOT_EVALUATED, entry(d, "1.3.3").conformance());
        assertEquals(Conformance.NOT_EVALUATED, entry(d, "1.2.1").conformance());
        assertTrue(entry(d, "1.2.1").remarks().contains("manual"));
        assertEquals(Level.A, entry(d, "2.4.7").level());
    }

    @Test
    void respectsTargetVersionAndLevel() {
        AuditReport r = report(WcagVersion.V2_1, Level.AA, f("reflow", "1.4.10", Outcome.PASSED));
        VpatDocument d = VpatGenerator.generate(r, VpatOptions.forProduct("P"));
        assertEquals(50, d.entries().size());
        assertTrue(d.entries().stream().anyMatch(e -> e.criterion().id().equals("4.1.1")));
        assertTrue(d.entries().stream().noneMatch(e -> e.criterion().id().equals("2.4.11")));
        assertEquals(Level.AA, entry(d, "2.4.7").level());
        assertTrue(d.toHtml().contains("Level AAA (No)"));
    }

    @Test
    void mergesMultipleReports() {
        AuditReport a = report(WcagVersion.V2_2, Level.AA, f("reflow", "1.4.10", Outcome.PASSED));
        AuditReport b = report(WcagVersion.V2_2, Level.AA, f("reflow", "1.4.10", Outcome.FAILED));
        VpatDocument d = VpatGenerator.generate(List.of(a, b), VpatOptions.forProduct("P"));
        assertEquals(Conformance.PARTIALLY_SUPPORTS, entry(d, "1.4.10").conformance());
        assertEquals(2, d.reportsMerged());
        assertTrue(d.toMarkdown().contains("Partially Supports"));
    }
}
