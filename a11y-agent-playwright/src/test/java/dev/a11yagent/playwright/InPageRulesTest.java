package dev.a11yagent.playwright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InPageRulesTest extends BrowserTestBase {

    private AuditReport bad;

    @BeforeEach
    void auditBadPage() {
        page.navigate(server.url("/bad.html"));
        bad = agent().audit("bad");
    }

    @Test
    void altTextQualityFlagsFileNamesGenericWordsAndEmptyAltInLinks() {
        List<Finding> failed = findings(bad, "alt-text-quality", Outcome.FAILED);
        assertEquals(3, failed.size(), () -> "expected 3 alt failures, got " + failed);
        assertTrue(failed.stream().anyMatch(f -> f.message().contains("file name")));
        assertTrue(failed.stream().anyMatch(f -> f.message().contains("generic")));
        assertTrue(failed.stream().anyMatch(f -> f.message().contains("only content of a link")));
    }

    @Test
    void linkPurposeFlagsGenericAndAmbiguousLinks() {
        List<Finding> issues = findings(bad, "link-purpose-in-context").stream().filter(f -> f.outcome().isIssue()).toList();
        assertTrue(issues.stream().anyMatch(f -> f.message().contains("\"click here\"")), () -> issues.toString());
        assertTrue(issues.stream().anyMatch(f -> f.message().contains("\"Read more\"")), () -> issues.toString());
        assertFalse(findings(bad, "link-purpose-link-only", Outcome.FAILED).isEmpty());
    }

    @Test
    void useOfColorFlagsInlineLinkWithoutUnderline() {
        List<Finding> failed = findings(bad, "use-of-color-links", Outcome.FAILED);
        assertEquals(1, failed.size(), () -> failed.toString());
        assertTrue(failed.get(0).target().html().contains("/terms"));
    }

    @Test
    void sensoryCharacteristicsNeedsReview() {
        List<Finding> review = findings(bad, "sensory-characteristics", Outcome.NEEDS_REVIEW);
        assertFalse(review.isEmpty());
        assertTrue(review.get(0).message().toLowerCase().contains("green button"));
    }

    @Test
    void targetSizeFlagsAdjacentTinyIconButtons() {
        List<Finding> failed = findings(bad, "target-size-minimum", Outcome.FAILED);
        assertEquals(2, failed.stream().filter(f -> f.target().html().contains("icon-btn")).count(), () -> failed.toString());
        assertFalse(findings(bad, "target-size-enhanced", Outcome.FAILED).isEmpty());
    }

    @Test
    void labelInNameFlagsMismatch() {
        List<Finding> failed = findings(bad, "label-in-name", Outcome.FAILED);
        assertEquals(1, failed.size(), () -> failed.toString());
        assertTrue(failed.get(0).message().contains("send message"));
    }

    @Test
    void movingContentAndTimingAreFlagged() {
        assertTrue(findings(bad, "pause-stop-hide", Outcome.FAILED).stream().anyMatch(f -> f.message().contains("marquee")));
        assertEquals(1, findings(bad, "timing-adjustable", Outcome.FAILED).size());
    }

    @Test
    void keyboardOperableFlagsClickableDiv() {
        List<Finding> failed = findings(bad, "keyboard-operable-controls", Outcome.FAILED);
        assertTrue(failed.stream().anyMatch(f -> f.target().html().contains("onclick")), () -> failed.toString());
    }

    @Test
    void authenticationAndInputPurpose() {
        List<Finding> auth = findings(bad, "accessible-authentication-minimum", Outcome.FAILED);
        assertEquals(1, auth.size(), () -> auth.toString());
        assertTrue(auth.get(0).message().contains("paste is blocked"));
        List<Finding> purpose = findings(bad, "identify-input-purpose", Outcome.FAILED);
        assertTrue(purpose.stream().anyMatch(f -> f.evidence().data().get("expectedToken").equals("email")), () -> purpose.toString());
    }

    @Test
    void headingsAndLabelsFlagPlaceholders() {
        List<Finding> failed = findings(bad, "headings-and-labels-descriptive", Outcome.FAILED);
        assertTrue(failed.stream().anyMatch(f -> f.message().contains("\"Heading\"")), () -> failed.toString());
        assertTrue(failed.stream().anyMatch(f -> f.message().contains("\"Field\"")), () -> failed.toString());
    }

    @Test
    void draggingAndTitleTooltipsNeedReview() {
        assertEquals(1, findings(bad, "dragging-movements", Outcome.NEEDS_REVIEW).size());
        assertEquals(1, findings(bad, "content-on-hover-title", Outcome.NEEDS_REVIEW).size());
    }

    @Test
    void cleanPageHasNoFailures() {
        page.navigate(server.url("/good.html"));
        AuditReport good = agent().audit("good");
        List<Finding> failed = good.allFindings().stream().filter(f -> f.outcome() == Outcome.FAILED).toList();
        assertTrue(failed.isEmpty(), () -> "unexpected failures on good page: " + failed);
        assertTrue(good.count(Outcome.PASSED) > 10);
    }
}
