package dev.a11yagent.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JudgeTest {

    @Test
    void parsesJsonEvenWhenWrappedInProse() {
        Verdict v = Judge.parse("Sure! Here is my answer:\n```json\n{\"result\":\"fail\",\"confidence\":0.9,\"rationale\":\"Alt text says logo but image is a chart\"}\n```", "test/model");
        assertEquals(Verdict.Result.FAIL, v.result());
        assertEquals(0.9, v.confidence(), 1e-9);
        assertTrue(v.rationale().contains("chart"));
        assertEquals("test/model", v.model());
    }

    @Test
    void unparseableAnswersBecomeUnsure() {
        Verdict v = Judge.parse("I cannot tell.", "m");
        assertEquals(Verdict.Result.UNSURE, v.result());
        assertEquals(0.0, v.confidence());
    }

    @Test
    void clampsConfidenceAndNormalisesResult() {
        Verdict v = Judge.parse("{\"result\":\"PASSED\",\"confidence\":7}", "m");
        assertEquals(Verdict.Result.PASS, v.result());
        assertEquals(1.0, v.confidence());
    }

    @Test
    void budgetIsEnforced() {
        ModelClient fake = new ModelClient() {
            @Override public String id() { return "fake"; }
            @Override public ModelResponse complete(ModelRequest request) {
                return new ModelResponse("{\"result\":\"PASS\",\"confidence\":1}", "fake", 1, 1);
            }
        };
        Judge judge = new Judge(fake, 1);
        assertEquals(Verdict.Result.PASS, judge.judge("q", List.of()).result());
        assertFalse(judge.hasBudget());
        assertEquals(Verdict.Result.UNSURE, judge.judge("q", List.of()).result());
    }

    @Test
    void modelClientsFromEnv() {
        assertTrue(ModelClients.fromEnv(Map.of()).isEmpty());
        Optional<ModelClient> anthropic = ModelClients.fromEnv(Map.of("ANTHROPIC_API_KEY", "k", "A11Y_AI_MODEL", "claude-x"));
        assertEquals("anthropic/claude-x", anthropic.orElseThrow().id());
        Optional<ModelClient> ollama = ModelClients.fromEnv(Map.of("A11Y_AI_PROVIDER", "ollama", "A11Y_AI_MODEL", "llava"));
        assertEquals("ollama/llava", ollama.orElseThrow().id());
        assertTrue(ModelClients.fromEnv(Map.of("A11Y_AI_PROVIDER", "none", "OPENAI_API_KEY", "k")).isEmpty());
    }
}
