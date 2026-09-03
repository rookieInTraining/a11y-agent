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

/** Anthropic Messages API client (vision capable). */
public final class AnthropicClient implements ModelClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public AnthropicClient(String apiKey, String model) {
        this(apiKey, model, "https://api.anthropic.com");
    }

    public AnthropicClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public String id() {
        return "anthropic/" + model;
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 600);
        body.put("system", request.system());
        ArrayNode content = JSON.createArrayNode();
        for (byte[] png : request.images()) {
            ObjectNode img = content.addObject();
            img.put("type", "image");
            ObjectNode src = img.putObject("source");
            src.put("type", "base64");
            src.put("media_type", "image/png");
            src.put("data", Base64.getEncoder().encodeToString(png));
        }
        content.addObject().put("type", "text").put("text", request.user());
        body.putArray("messages").addObject().put("role", "user").set("content", content);

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                .timeout(Duration.ofSeconds(120))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("Anthropic API " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode n = JSON.readTree(resp.body());
            StringBuilder text = new StringBuilder();
            for (JsonNode block : n.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            return new ModelResponse(text.toString(), id(),
                    n.path("usage").path("input_tokens").asLong(0),
                    n.path("usage").path("output_tokens").asLong(0));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Anthropic API call failed", e);
        }
    }
}
