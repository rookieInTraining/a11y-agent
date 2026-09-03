package dev.a11yagent.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;

/**
 * Turns a {@link ModelClient} into a WCAG judge: builds criterion-specific prompts, forces a JSON
 * answer and parses it into a {@link Verdict}. Every prompt asks for a verdict, a confidence and a
 * rationale so that AI results are auditable and can be down-graded to NEEDS_REVIEW in reports.
 */
public final class Judge {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM = """
            You are a senior accessibility auditor (WCAG 2.2, WAI-ARIA 1.2, ACT Rules). You judge one
            specific success criterion for one specific element, using the evidence provided (text and,
            when present, a screenshot crop). Be strict but fair: apply the normative text of the criterion,
            not general best practice. If the evidence is insufficient answer UNSURE.
            Respond with ONLY a JSON object: {"result":"PASS"|"FAIL"|"UNSURE","confidence":0.0-1.0,"rationale":"one or two sentences"}
            """;

    private final ModelClient client;
    private final int budget;
    private int used;

    public Judge(ModelClient client, int budget) {
        this.client = client;
        this.budget = budget;
    }

    public String modelId() {
        return client.id();
    }

    public boolean hasBudget() {
        return used < budget;
    }

    public int used() {
        return used;
    }

    /**
     * Generic judgement: {@code question} describes criterion + element; {@code images} are optional
     * PNG crops.
     */
    public Verdict judge(String question, List<byte[]> images) {
        if (!hasBudget()) {
            return Verdict.unsure("AI judgement budget exhausted", client.id());
        }
        used++;
        ModelResponse resp;
        try {
            resp = client.complete(new ModelRequest(SYSTEM, question, images));
        } catch (RuntimeException e) {
            return Verdict.unsure("Model call failed: " + e.getMessage(), client.id());
        }
        return parse(resp.text(), client.id());
    }

    static Verdict parse(String text, String model) {
        String body = text == null ? "" : text.trim();
        int start = body.indexOf('{');
        int end = body.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Verdict.unsure("Unparseable model answer: " + abbreviate(body), model);
        }
        try {
            JsonNode n = JSON.readTree(body.substring(start, end + 1));
            String r = n.path("result").asText("UNSURE").toUpperCase(Locale.ROOT);
            Verdict.Result result = switch (r) {
                case "PASS", "PASSED" -> Verdict.Result.PASS;
                case "FAIL", "FAILED" -> Verdict.Result.FAIL;
                default -> Verdict.Result.UNSURE;
            };
            double conf = n.path("confidence").asDouble(0.5);
            conf = Math.max(0, Math.min(1, conf));
            return new Verdict(result, conf, n.path("rationale").asText(""), model);
        } catch (Exception e) {
            return Verdict.unsure("Unparseable model answer: " + abbreviate(body), model);
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
