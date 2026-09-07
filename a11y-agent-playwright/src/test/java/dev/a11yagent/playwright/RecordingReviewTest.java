package dev.a11yagent.playwright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.a11yagent.core.ai.ModelClient;
import dev.a11yagent.core.ai.ModelRequest;
import dev.a11yagent.core.ai.ModelResponse;
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Outcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RecordingReviewTest extends BrowserTestBase {

    @Test
    void deterministicPassIsNotOverriddenByAFailingModel() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Path dir = Files.createTempDirectory("a11y-agent-review-pass");
        A11yConfig config = A11yConfig.builder()
                .artifactsDir(dir)
                .recordVideo(false)
                .screenshots(false)
                .modelClient(failingClient(calls))
                .build();
        page.navigate(server.url("/good.html"));
        AuditReport report = A11yAgent.forPage(page, config).check("2.1.2");
        List<Finding> traps = findings(report, "no-keyboard-trap");
        assertEquals(1, traps.size(), traps::toString);
        assertEquals(Outcome.PASSED, traps.get(0).outcome(), traps.get(0)::message);
        assertFalse(traps.get(0).evidence().aiJudged());
        assertEquals(0, calls.get());
    }

    @Test
    void leftoverCantTellKeyboardProbeCanBeFailedFromTheReview() throws Exception {
        Path dir = Files.createTempDirectory("a11y-agent-review-leftover");
        A11yConfig config = A11yConfig.builder()
                .artifactsDir(dir)
                .recordVideo(false)
                .screenshots(false)
                .maxFocusStops(1)
                .modelClient(failingClient(new AtomicInteger()))
                .build();
        page.navigate(server.url("/dom-bad.html"));
        AuditReport report = A11yAgent.forPage(page, config).check("2.1.2");
        Finding f = findings(report, "no-keyboard-trap").get(0);
        assertEquals(Outcome.FAILED, f.outcome(), f::message);
        assertTrue(f.evidence().aiJudged(), f::message);
        assertEquals(true, f.evidence().data().get("videoEnrichment"));
        String html = dev.a11yagent.core.report.HtmlReportWriter.render(report);
        assertTrue(html.contains("reviewed against the audit recording"), html);
    }

    private static ModelClient failingClient(AtomicInteger calls) {
        return new ModelClient() {
            @Override
            public String id() {
                return "fake/review";
            }

            @Override
            public ModelResponse complete(ModelRequest request) {
                calls.incrementAndGet();
                return new ModelResponse(
                        "{\"result\":\"FAIL\",\"confidence\":0.95,\"rationale\":\"Focus cannot leave; leftover is a trap.\"}",
                        "fake/review", 1, 1);
            }
        };
    }
}
