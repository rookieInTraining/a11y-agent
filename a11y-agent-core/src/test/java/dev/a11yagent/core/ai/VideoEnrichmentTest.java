package dev.a11yagent.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.a11yagent.core.model.Evidence;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.Target;
import dev.a11yagent.core.rules.ArtifactStore;
import dev.a11yagent.core.wcag.Wcag;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VideoEnrichmentTest {

    @Test
    void alertDuringKeyboardProbeBecomesFailed() throws Exception {
        Finding leftover = leftover("no-keyboard-trap", "2.1.2", Outcome.CANT_TELL,
                "Rule no-keyboard-trap failed to execute: UnhandledAlertException: unexpected alert open: {Alert text : Keyboard Trap! Tab key is disabled.}");
        Judge judge = judge("{\"result\":\"FAIL\",\"confidence\":0.92,\"rationale\":\"A JS alert intercepted Tab; that is a keyboard trap.\"}");
        Finding out = VideoEnrichment.apply(judge, List.of(leftover), List.of(),
                new ArtifactStore(Files.createTempDirectory("ve"))).get(0);
        assertEquals(Outcome.FAILED, out.outcome());
        assertEquals(Impact.CRITICAL, out.impact());
        assertTrue(out.evidence().aiJudged());
        assertEquals(true, out.evidence().data().get(VideoEnrichment.DATA_FLAG));
        assertTrue(out.message().contains("Recording review: FAIL"));
    }

    @Test
    void deterministicFailAndPassAreNotSentToTheModel() {
        AtomicInteger calls = new AtomicInteger();
        Judge judge = new Judge(new ModelClient() {
            @Override public String id() { return "fake"; }
            @Override public ModelResponse complete(ModelRequest request) {
                calls.incrementAndGet();
                return new ModelResponse("{\"result\":\"FAIL\",\"confidence\":1,\"rationale\":\"no\"}", "fake", 1, 1);
            }
        }, 10);
        Finding fail = leftover("color-contrast", "1.4.3", Outcome.FAILED, "ratio 2.8:1");
        Finding pass = leftover("no-keyboard-trap", "2.1.2", Outcome.PASSED, "Focus moved through 4 stops");
        List<Finding> out = VideoEnrichment.apply(judge, List.of(fail, pass), List.of(), null);
        assertEquals(0, calls.get());
        assertEquals(Outcome.FAILED, out.get(0).outcome());
        assertEquals(Outcome.PASSED, out.get(1).outcome());
        assertFalse(out.get(0).evidence().aiJudged());
    }

    @Test
    void passOnCantTellKeepsCantTellButQuotesTheModel() throws Exception {
        Finding leftover = leftover("no-keyboard-trap", "2.1.2", Outcome.CANT_TELL, "Tab traversal stopped after 150 stops");
        Judge judge = judge("{\"result\":\"PASS\",\"confidence\":0.9,\"rationale\":\"Focus is cycling the full page.\"}");
        Finding out = VideoEnrichment.apply(judge, List.of(leftover), List.of(),
                new ArtifactStore(Files.createTempDirectory("ve"))).get(0);
        assertEquals(Outcome.CANT_TELL, out.outcome());
        assertTrue(out.evidence().aiJudged());
        assertTrue(out.message().contains("Recording review: PASS"));
    }

    @Test
    void alreadyJudgedLeftoversAreNotReReviewed() {
        AtomicInteger calls = new AtomicInteger();
        Judge judge = new Judge(new ModelClient() {
            @Override public String id() { return "fake"; }
            @Override public ModelResponse complete(ModelRequest request) {
                calls.incrementAndGet();
                return new ModelResponse("{\"result\":\"FAIL\",\"confidence\":1,\"rationale\":\"x\"}", "fake", 1, 1);
            }
        }, 10);
        Finding judged = leftover("focus-visible", "2.4.7", Outcome.NEEDS_REVIEW, "subtle")
                .withEvidence(new Evidence(null, "maybe a ring", "anthropic/x", 0.5, Map.of()));
        VideoEnrichment.apply(judge, List.of(judged), List.of(), null);
        assertEquals(0, calls.get());
    }

    @Test
    void lowConfidenceFailBecomesNeedsReview() throws Exception {
        Finding leftover = leftover("no-keyboard-trap", "2.1.2", Outcome.CANT_TELL, "could not confirm");
        Judge judge = judge("{\"result\":\"FAIL\",\"confidence\":0.4,\"rationale\":\"Looks stuck but unclear.\"}");
        Finding out = VideoEnrichment.apply(judge, List.of(leftover), List.of(),
                new ArtifactStore(Files.createTempDirectory("ve"))).get(0);
        assertEquals(Outcome.NEEDS_REVIEW, out.outcome());
    }

    @Test
    void statusMessagesLeftoverPromptMentionsLiveRegions() {
        Finding leftover = leftover("status-messages", "4.1.3", Outcome.CANT_TELL,
                "Wrap the action that should announce results in observe()");
        String prompt = VideoEnrichment.prompt(leftover, 0);
        assertTrue(prompt.contains("aria-live"));
        assertTrue(prompt.contains("4.1.3"));
    }

    private static Judge judge(String json) {
        return new Judge(new ModelClient() {
            @Override public String id() { return "fake/model"; }
            @Override public ModelResponse complete(ModelRequest request) {
                return new ModelResponse(json, "fake/model", 1, 1);
            }
        }, 10);
    }

    private static Finding leftover(String rule, String sc, Outcome o, String message) {
        return new Finding(rule, Set.of(Wcag.get(sc)), o, Impact.MINOR, message,
                new Target("html", "", null), Evidence.deterministic(message), "page", "https://example.test/");
    }
}
