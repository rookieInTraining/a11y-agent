package dev.a11yagent.core.ai;

public record ModelResponse(String text, String model, long inputTokens, long outputTokens) {
}
