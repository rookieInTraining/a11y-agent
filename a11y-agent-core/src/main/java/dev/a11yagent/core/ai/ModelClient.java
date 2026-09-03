package dev.a11yagent.core.ai;

/**
 * A (multimodal) language model. Implementations exist for the Anthropic Messages API and any
 * OpenAI-compatible chat completions endpoint (OpenAI, Ollama, LM Studio, vLLM, ...).
 */
public interface ModelClient {

    /** Human readable identifier used in reports, e.g. "anthropic/claude-...". */
    String id();

    ModelResponse complete(ModelRequest request);
}
