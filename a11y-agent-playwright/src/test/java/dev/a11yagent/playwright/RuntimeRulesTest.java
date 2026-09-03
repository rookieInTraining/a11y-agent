package dev.a11yagent.playwright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeRulesTest extends BrowserTestBase {

    @Test
    void focusVisibleFailsWhenOutlineIsSuppressed() {
        page.navigate(server.url("/bad.html"));
        AuditReport r = agent().check("2.4.7");
        List<Finding> failed = findings(r, "focus-visible", Outcome.FAILED);
        assertTrue(failed.size() >= 5, () -> "expected many focus-visible failures, got " + failed);
        assertTrue(failed.stream().allMatch(f -> f.evidence().screenshot() != null || failed.indexOf(f) >= 15));
    }

    @Test
    void focusVisiblePassesWithFocusRing() {
        page.navigate(server.url("/good.html"));
        AuditReport r = agent().check("focus-visible");
        assertTrue(findings(r, "focus-visible", Outcome.FAILED).isEmpty());
        assertTrue(findings(r, "focus-visible", Outcome.PASSED).size() >= 5);
    }

    @Test
    void focusOrderFlagsPositiveTabindexAndOffscreenFocus() {
        page.navigate(server.url("/bad.html"));
        AuditReport r = agent().check("2.4.3");
        assertTrue(findings(r, "focus-order", Outcome.NEEDS_REVIEW).stream().anyMatch(f -> f.message().contains("tabindex=\"3\"")));
        assertTrue(findings(r, "focus-order", Outcome.FAILED).stream().anyMatch(f -> f.message().contains("outside the viewport")));
    }

    @Test
    void fixedFooterObscuresFocusedLink() {
        page.navigate(server.url("/bad.html"));
        AuditReport r = agent().check("2.4.11");
        List<Finding> failed = findings(r, "focus-not-obscured-minimum", Outcome.FAILED);
        assertFalse(failed.isEmpty(), () -> r.allFindings().toString());
        assertTrue(failed.stream().anyMatch(f -> f.message().contains("fixed-footer")), () -> failed.toString());
    }

    @Test
    void keyboardTrapIsDetected() {
        page.navigate(server.url("/trap.html"));
        AuditReport r = agent().check("2.1.2");
        List<Finding> failed = findings(r, "no-keyboard-trap", Outcome.FAILED);
        assertEquals(1, failed.size(), () -> r.allFindings().toString());
        assertTrue(failed.get(0).target().selector().contains("trapped"));

        page.navigate(server.url("/good.html"));
        assertEquals(1, findings(agent().check("no-keyboard-trap"), "no-keyboard-trap", Outcome.PASSED).size());
    }

    @Test
    void reflowFailsOnFixedWidthContent() {
        page.navigate(server.url("/bad.html"));
        AuditReport r = agent().check("reflow");
        List<Finding> failed = findings(r, "reflow", Outcome.FAILED);
        assertTrue(failed.stream().anyMatch(f -> f.target().html().contains("wide")), () -> r.allFindings().toString());
        assertEquals(1280, page.viewportSize().width, "viewport must be restored");

        page.navigate(server.url("/good.html"));
        assertEquals(1, findings(agent().check("1.4.10"), "reflow", Outcome.PASSED).size());
    }

    @Test
    void textSpacingDetectsClipping() {
        page.navigate(server.url("/bad.html"));
        AuditReport r = agent().check("1.4.12");
        List<Finding> failed = findings(r, "text-spacing", Outcome.FAILED);
        assertTrue(failed.stream().anyMatch(f -> f.target().html().contains("clip")), () -> r.allFindings().toString());
        Object stillInjected = page.evaluate("() => !!document.getElementById('__a11y_agent_text_spacing')");
        assertEquals(false, stillInjected);

        page.navigate(server.url("/good.html"));
        assertEquals(1, findings(agent().check("text-spacing"), "text-spacing", Outcome.PASSED).size());
    }

    @Test
    void checkByCriterionWithoutRulesReportsCoverageGap() {
        page.navigate(server.url("/good.html"));
        AuditReport r = agent().check("1.2.2");
        assertEquals(1, r.allFindings().size());
        assertEquals(Outcome.CANT_TELL, r.allFindings().get(0).outcome());
    }
}
