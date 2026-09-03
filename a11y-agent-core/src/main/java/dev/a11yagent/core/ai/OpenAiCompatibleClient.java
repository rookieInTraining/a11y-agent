package dev.a11yagent.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Chat Completions client for OpenAI and OpenAI-compatible servers (Ollama at
 * {@code http://localhost:11434/v1}, LM Studio, vLLM, OpenRouter, ...).
 */
public final class OpenAiCompatibleClient implements ModelClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final String vendor;

    public OpenAiCompatibleClient(String apiKey, String model, String baseUrl, String vendor) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.vendor = vendor;
    }

    public static OpenAiCompatibleClient openAi(String apiKey, String model) {
        return new OpenAiCompatibleClient(apiKey, model, "https://api.openai.com/v1", "openai");
    }

    public static OpenAiCompatibleClient ollama(String model, String baseUrl) {
        return new OpenAiCompatibleClient("ollama", model, baseUrl == null ? "http://localhost:11434/v1" : baseUrl, "ollama");
    }

    @Override
    public String id() {
        return vendor + "/" + model;
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.system());
        ObjectNode user = messages.addObject().put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject().put("type", "text").put("text", request.user());
        for (byte[] png : request.images()) {
            ObjectNode img = content.addObject();
            img.put("type", "image_url");
            img.putObject("image_url").put("url", "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(180))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (!apiKey.isBlank()) {
            rb.header("authorization", "Bearer " + apiKey);
        }
        try {
            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException(vendor + " API " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode n = JSON.readTree(resp.body());
            String text = n.path("choices").path(0).path("message").path("content").asText("");
            return new ModelResponse(text, id(),
                    n.path("usage").path("prompt_tokens").asLong(0),
                    n.path("usage").path("completion_tokens").asLong(0));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(vendor + " API call failed", e);
        }
    }
}
