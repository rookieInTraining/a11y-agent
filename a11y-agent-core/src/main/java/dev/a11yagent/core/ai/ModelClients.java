package dev.a11yagent.core.ai;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a {@link ModelClient} from environment variables so CI can enable AI judgements without code
 * changes:
 *
 * <pre>
 *   A11Y_AI_PROVIDER = anthropic | openai | ollama | none   (default: auto-detect from API keys)
 *   A11Y_AI_MODEL    = model name (provider specific default when unset)
 *   A11Y_AI_BASE_URL = override endpoint (Ollama, proxies, Azure-style gateways)
 *   ANTHROPIC_API_KEY / OPENAI_API_KEY
 * </pre>
 */
public final class ModelClients {

    private ModelClients() {
    }

    public static Optional<ModelClient> fromEnv() {
        return fromEnv(System.getenv());
    }

    public static Optional<ModelClient> fromEnv(Map<String, String> env) {
        String provider = env.getOrDefault("A11Y_AI_PROVIDER", "").toLowerCase(Locale.ROOT).trim();
        String model = env.get("A11Y_AI_MODEL");
        String baseUrl = env.get("A11Y_AI_BASE_URL");
        String anthropicKey = env.get("ANTHROPIC_API_KEY");
        String openAiKey = env.get("OPENAI_API_KEY");

        if (provider.isEmpty()) {
            if (anthropicKey != null && !anthropicKey.isBlank()) {
                provider = "anthropic";
            } else if (openAiKey != null && !openAiKey.isBlank()) {
                provider = "openai";
            } else {
                return Optional.empty();
            }
        }
        return switch (provider) {
            case "none", "off", "false" -> Optional.empty();
            case "anthropic" -> {
                if (anthropicKey == null || anthropicKey.isBlank()) {
                    throw new IllegalStateException("A11Y_AI_PROVIDER=anthropic requires ANTHROPIC_API_KEY");
                }
                String m = model == null ? "claude-sonnet-4-5" : model;
                yield Optional.of(baseUrl == null ? new AnthropicClient(anthropicKey, m) : new AnthropicClient(anthropicKey, m, baseUrl));
            }
            case "openai" -> {
                if (openAiKey == null || openAiKey.isBlank()) {
                    throw new IllegalStateException("A11Y_AI_PROVIDER=openai requires OPENAI_API_KEY");
                }
                String m = model == null ? "gpt-4o" : model;
                yield Optional.of(baseUrl == null ? OpenAiCompatibleClient.openAi(openAiKey, m)
                        : new OpenAiCompatibleClient(openAiKey, m, baseUrl, "openai"));
            }
            case "ollama" -> Optional.of(OpenAiCompatibleClient.ollama(model == null ? "llama3.2-vision" : model, baseUrl));
            default -> throw new IllegalArgumentException("Unknown A11Y_AI_PROVIDER: " + provider);
        };
    }
}
