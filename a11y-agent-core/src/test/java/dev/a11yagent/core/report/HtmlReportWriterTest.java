package dev.a11yagent.core.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.PageAudit;
import dev.a11yagent.core.model.Target;
import dev.a11yagent.core.rules.AiAssist;
import dev.a11yagent.core.rules.Rules;
import dev.a11yagent.core.wcag.Level;
import dev.a11yagent.core.wcag.Wcag;
import dev.a11yagent.core.wcag.WcagVersion;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HtmlReportWriterTest {

    @Test
    void catalogueMarksAltTextAsAiOnlyAndFocusVisibleAsEnhanced() {
        assertEqualsAssist(AiAssist.AI_ONLY, "alt-text-quality");
        assertEqualsAssist(AiAssist.ENHANCED, "focus-visible");
        assertEqualsAssist(AiAssist.NONE, "color-contrast");
    }

    @Test
    void reportSeparatesDeterministicAiEnhancedAndAiOnly() {
        Finding heuristic = finding("color-contrast", "1.4.3", Outcome.FAILED,
                Evidence.deterministic("ratio 2.8:1"));
        Finding aiOnly = finding("alt-text-quality", "1.1.1", Outcome.FAILED,
                new Evidence("screenshots/alt.png", "Alt describes a logo but the image is a pie chart",
                        "anthropic/claude-test", 0.91, Map.of("aiVerdict", "FAIL")));
        Finding aiEnhanced = finding("focus-visible", "2.4.7", Outcome.PASSED,
                new Evidence("screenshots/focus.png", "A 2px ring is visible around the button",
                        "anthropic/claude-test", 0.82, Map.of("aiVerdict", "PASS")));
        Finding heuristicOnAiRule = finding("alt-text-quality", "1.1.1", Outcome.FAILED,
                Evidence.deterministic("alt is a file name"));

        AuditReport r = report("anthropic/claude-test", heuristic, aiOnly, aiEnhanced, heuristicOnAiRule);
        String html = HtmlReportWriter.render(r);

        assertTrue(html.contains("id=\"analysis\""), html);
        assertTrue(html.contains("Vision/language model"), html);
        assertTrue(html.contains("anthropic/claude-test"), html);
        assertTrue(html.contains("2 AI judgement"), html);

        assertTrue(html.contains("AI-only"), html);
        assertTrue(html.contains("AI-enhanced"), html);
        assertTrue(html.contains("Deterministic"), html);

        assertTrue(html.contains("id=\"ai-judgements\""), html);
        assertTrue(html.contains("Alt describes a logo but the image is a pie chart"), html);
        assertTrue(html.contains("A 2px ring is visible around the button"), html);
        assertTrue(html.contains("0.91"), html);
        assertTrue(html.contains("0.82"), html);

        assertTrue(html.contains("alt is a file name"), html);
        int aiOnlyBadgesOnHeuristicFilename = count(html, "AI-only");
        assertTrue(aiOnlyBadgesOnHeuristicFilename >= 1);
    }

    @Test
    void withoutAModelTheReportSaysAiWasNotConfigured() {
        Finding f = finding("alt-text-quality", "1.1.1", Outcome.FAILED, Evidence.deterministic("generic alt"));
        String html = HtmlReportWriter.render(report(null, f));
        assertTrue(html.contains("No vision/language model was configured"), html);
        assertFalse(html.contains("id=\"ai-judgements\""), html);
        assertTrue(html.contains("AI-only"), html);
        assertTrue(html.contains("ran as heuristics"), html);
    }

    @Test
    void reportEmbedsTheWebmWhenAVideoPathIsPresent() {
        Finding f = finding("color-contrast", "1.4.3", Outcome.FAILED, Evidence.deterministic("ratio 2.8:1"));
        AuditReport r = new AuditReport("page", Instant.EPOCH, Instant.EPOCH, WcagVersion.V2_2, Level.AA,
                Set.of("color-contrast"),
                List.of(new PageAudit("page", "https://example.test/", "Example", null, List.of(f))),
                List.of(), null, "audit.webm");
        String html = HtmlReportWriter.render(r);
        assertTrue(html.contains("id=\"recording\""), html);
        assertTrue(html.contains("type=\"video/webm\""), html);
        assertTrue(html.contains("src=\"audit.webm\""), html);
        assertFalse(HtmlReportWriter.render(report(null, f)).contains("id=\"recording\""));
        Finding withShot = finding("color-contrast", "1.4.3", Outcome.FAILED,
                new Evidence("screenshots/x.png", "ratio 2.8:1", null, 1.0, Map.of()));
        String withImage = HtmlReportWriter.render(report(null, withShot));
        assertTrue(withImage.contains("<figure class=\"shot\">"), withImage);
        assertTrue(withImage.contains("<figcaption>"), withImage);
    }

    @Test
    void reportMentionsPostRunRecordingReview() {
        Finding reviewed = finding("no-keyboard-trap", "2.1.2", Outcome.FAILED,
                new Evidence(null, "A JS alert intercepted Tab", "fake/model", 0.92,
                        Map.of("videoEnrichment", true, "aiVerdict", "FAIL")));
        String html = HtmlReportWriter.render(report("fake/model", reviewed));
        assertTrue(html.contains("reviewed against the audit recording"), html);
        assertTrue(html.contains("1 leftover finding was reviewed"), html);
    }

    private static void assertEqualsAssist(AiAssist expected, String ruleId) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, Rules.aiAssist(ruleId), ruleId);
    }

    private static AuditReport report(String model, Finding... findings) {
        return new AuditReport("page", Instant.EPOCH, Instant.EPOCH, WcagVersion.V2_2, Level.AA,
                Set.of("color-contrast", "alt-text-quality", "focus-visible"),
                List.of(new PageAudit("page", "https://example.test/", "Example", null, List.of(findings))),
                List.of(), model);
    }

    private static Finding finding(String rule, String sc, Outcome o, Evidence evidence) {
        return new Finding(rule, Set.of(Wcag.get(sc)), o, Impact.SERIOUS, evidence.rationale(),
                new Target("#x", "<img>", null), evidence, "page", "https://example.test/");
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = 0; (i = haystack.indexOf(needle, i)) >= 0; i += needle.length()) {
            n++;
        }
        return n;
    }
}
