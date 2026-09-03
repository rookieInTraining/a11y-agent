package dev.a11yagent.playwright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.a11yagent.core.ax.AxTree;
import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DomAndAxRulesTest extends BrowserTestBase {

    private AuditReport r;

    @BeforeEach
    void auditDomPage() {
        page.navigate(server.url("/dom-bad.html"));
        r = agent().audit("dom-bad");
    }

    private List<Finding> failed(String rule) {
        return findings(r, rule, Outcome.FAILED);
    }

    private List<Finding> review(String rule) {
        return findings(r, rule, Outcome.NEEDS_REVIEW);
    }

    @Test
    void contrast() {
        List<Finding> f = failed("color-contrast");
        assertEquals(1, f.size(), () -> f.toString());
        assertTrue(f.get(0).message().contains("2.85:1"), f.get(0).message());
        assertTrue(failed("color-contrast-enhanced").size() >= 1);
        page.navigate(server.url("/good.html"));
        assertTrue(findings(agent().check("1.4.3"), "color-contrast", Outcome.FAILED).isEmpty());
    }

    @Test
    void missingAltAndEmptyNames() {
        List<Finding> alt = failed("image-alt");
        assertEquals(2, alt.size(), () -> alt.toString());
        List<Finding> names = failed("control-name");
        assertTrue(names.stream().anyMatch(f -> f.target().html().startsWith("<a href=\"/empty\"")), () -> names.toString());
        assertTrue(names.stream().anyMatch(f -> f.target().html().startsWith("<button></button>")), () -> names.toString());
        assertTrue(names.stream().anyMatch(f -> f.target().html().contains("unlabelled")), () -> names.toString());
        assertTrue(names.stream().anyMatch(f -> f.target().html().startsWith("<iframe")), () -> names.toString());
        // link whose only content is an unnamed image
        assertTrue(names.stream().anyMatch(f -> f.target().html().startsWith("<a href=\"/promo\"")), () -> names.toString());
    }

    @Test
    void ariaValidity() {
        List<Finding> f = failed("aria-validity");
        assertTrue(f.stream().anyMatch(m -> m.message().contains("role=\"buton\"")), () -> f.toString());
        assertTrue(f.stream().anyMatch(m -> m.message().contains("requires aria-checked")), () -> f.toString());
        assertTrue(f.stream().anyMatch(m -> m.message().contains("role=\"listitem\" must be contained")), () -> f.toString());
        assertTrue(f.stream().anyMatch(m -> m.message().contains("aria-labelledby references id(s) that do not exist")), () -> f.toString());
        assertTrue(f.stream().anyMatch(m -> m.message().contains("aria-bogus is not a valid")), () -> f.toString());
        assertTrue(f.stream().anyMatch(m -> m.message().contains("aria-hidden=\"true\" subtree contains 1 keyboard-focusable")), () -> f.toString());
        assertTrue(f.stream().anyMatch(m -> m.message().contains("aria-expanded=\"maybe\"")), () -> f.toString());
        assertTrue(f.stream().anyMatch(m -> m.message().contains("aria-label is prohibited on <div>")), () -> f.toString());
        assertEquals(1, failed("duplicate-id-aria").size());
        assertEquals(1, failed("nested-interactive").size());
        assertTrue(failed("autocomplete-valid").get(0).message().contains("mail"));
    }

    @Test
    void structure() {
        List<Finding> lm = failed("landmarks");
        assertTrue(lm.stream().anyMatch(m -> m.message().contains("2 navigation landmarks have no name")), () -> lm.toString());
        assertTrue(review("landmarks").stream().anyMatch(m -> m.message().contains("No <main>")), () -> r.allFindings().toString());
        assertTrue(failed("heading-structure").stream().anyMatch(m -> m.message().contains("Empty heading (level 2)")));
        assertTrue(review("heading-structure").stream().anyMatch(m -> m.message().contains("jumps from h1 to h3")));
        assertEquals(1, failed("list-structure").size());
        assertEquals(1, failed("table-headers").size());
        assertEquals(1, failed("html-lang").size());
        assertEquals(1, failed("lang-attr-valid").size());
        assertEquals(1, failed("document-title").size());
        assertEquals(1, failed("meta-viewport-zoom").size());
        assertEquals(1, failed("scrollable-region-focusable").size());
        assertEquals(1, review("live-regions").size());
        assertFalse(findings(r, "bypass-blocks").isEmpty());
    }

    @Test
    void accessibilityTreeRules() {
        List<Finding> generic = failed("ax-focusable-without-role");
        assertTrue(generic.stream().anyMatch(f -> f.target().selector().equals("#custom")), () -> generic.toString());
        assertTrue(generic.stream().anyMatch(f -> f.target().selector().equals("#lang-picker")), () -> generic.toString());
        assertTrue(generic.stream().anyMatch(f -> f.target().selector().equals("#fake-skip") && f.message().contains("static role")), () -> generic.toString());
        assertTrue(generic.stream().noneMatch(f -> f.target().selector().equals("#programmatic-target")), () -> generic.toString());
        List<Finding> quality = review("ax-name-quality");
        assertTrue(quality.stream().anyMatch(f -> f.message().contains("repeats the role") && f.message().contains("Save changes button")), () -> quality.toString());
        assertTrue(quality.stream().anyMatch(f -> f.message().contains("pointer/keyboard instructions")), () -> quality.toString());
        assertTrue(r.rulesRun().contains("ax-name-quality"));
    }

    @Test
    void accessibilityTreeIsExposedToCallers() {
        AxTree tree = agent().accessibilityTree().orElseThrow();
        assertTrue(tree.countRole("heading") >= 2);
        assertTrue(tree.exposed().anyMatch(n -> "link".equals(n.role()) && "Alpha".equals(n.name())));
        String text = agent().accessibilityTreeText();
        assertTrue(text.contains("link \"Alpha\""), text);
        assertTrue(text.contains("heading \"Structure fixture\""), text);
    }

    @Test
    void cleanPageStillHasNoFailures() {
        page.navigate(server.url("/good.html"));
        AuditReport good = agent().audit("good");
        List<Finding> failedAll = good.allFindings().stream().filter(f -> f.outcome() == Outcome.FAILED).toList();
        assertTrue(failedAll.isEmpty(), () -> "unexpected failures on good page: " + failedAll);
    }
}
