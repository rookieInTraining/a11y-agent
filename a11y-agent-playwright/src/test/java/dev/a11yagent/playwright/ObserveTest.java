package dev.a11yagent.playwright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObserveTest extends BrowserTestBase {

    @Test
    void observeEmptySubmitFlagsUnassociatedErrors() {
        page.navigate(server.url("/form-errors.html"));
        AuditReport r = agent().observe("3.3.1", p -> p.click("#submit"));
        List<Finding> failed = findings(r, "error-identification", Outcome.FAILED);
        assertTrue(failed.stream().anyMatch(f -> f.message().contains("not programmatically associated")), () -> failed.toString());
    }

    @Test
    void observeSuccessWithoutLiveRegionFailsStatusMessages() {
        page.navigate(server.url("/form-errors.html"));
        page.fill("#name", "Jane");
        page.fill("#email", "jane@example.com");
        AuditReport r = agent().observe("4.1.3", p -> p.click("#submit"));
        List<Finding> failed = findings(r, "status-messages", Outcome.FAILED);
        assertEquals(1, failed.size(), () -> r.allFindings().toString());
        assertTrue(failed.get(0).message().contains("live region"), failed.get(0)::message);
    }

    @Test
    void snapshotStatusCheckCantTellWithoutObserve() {
        page.navigate(server.url("/form-errors.html"));
        AuditReport r = agent().check("4.1.3");
        assertTrue(r.allFindings().stream()
                .filter(f -> f.ruleId().equals("status-messages"))
                .anyMatch(f -> f.outcome() == Outcome.CANT_TELL));
    }

    @Test
    void placeholderContrastFailsOnLowContrastPlaceholder() {
        page.navigate(server.url("/contrast-placeholder.html"));
        AuditReport r = agent().check("1.4.3");
        List<Finding> failed = findings(r, "color-contrast", Outcome.FAILED);
        assertTrue(failed.stream().anyMatch(f -> f.message().contains("Placeholder")
                || (f.evidence().data().get("check") != null && f.evidence().data().get("check").equals("placeholder-contrast"))),
                () -> failed.toString());
    }

    @Test
    void widgetTrapDetectedAfterOpeningCalendar() {
        page.navigate(server.url("/widgets.html"));
        AuditReport r = agent().check("2.1.2");
        List<Finding> traps = findings(r, "no-keyboard-trap");
        assertEquals(1, traps.size(), traps::toString);
        assertEquals(Outcome.FAILED, traps.get(0).outcome(), traps.get(0)::message);
    }
}
