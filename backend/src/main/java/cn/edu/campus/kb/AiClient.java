package cn.edu.campus.kb;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class AiClient {
    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final boolean embeddingEnabled;
    private final String embeddingModel;

    public AiClient(@Value("${app.ai.base-url}") String baseUrl,
                    @Value("${app.ai.api-key}") String apiKey,
                    @Value("${app.ai.model}") String model,
                    @Value("${app.ai.embedding-enabled}") boolean embeddingEnabled,
                    @Value("${app.ai.embedding-model}") String embeddingModel) {
        this.apiKey = apiKey; this.model = model;
        this.embeddingEnabled = embeddingEnabled; this.embeddingModel = embeddingModel;
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(45));
        this.client = RestClient.builder().baseUrl(baseUrl.replaceAll("/+$", ""))
                .requestFactory(factory).defaultHeader("Authorization", "Bearer " + apiKey).build();
    }

    public boolean enabled() { return !apiKey.isBlank(); }
    public boolean embeddingsEnabled() { return enabled() && embeddingEnabled; }
    public String model() { return model; }
    public String embeddingModel() { return embeddingModel; }

    public String answer(List<Map<String, String>> messages) {
        JsonNode response = client.post().uri("/chat/completions")
                .body(Map.of("model", model, "messages", messages, "temperature", 0.2, "max_tokens", 1600))
                .retrieve().body(JsonNode.class);
        String answer = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText("").strip();
        if (answer.isBlank() || answer.length() > 16000) throw new IllegalStateException("Empty or oversized model response");
        return answer;
    }

    public List<double[]> embed(List<String> texts) {
        List<double[]> result = new ArrayList<>();
        for (int offset = 0; offset < texts.size(); offset += 16) {
            List<String> batch = texts.subList(offset, Math.min(offset + 16, texts.size()));
            JsonNode response = client.post().uri("/embeddings")
                    .body(Map.of("model", embeddingModel, "input", batch))
                    .retrieve().body(JsonNode.class);
            if (response == null || !response.path("data").isArray() || response.path("data").size() != batch.size())
                throw new IllegalStateException("Invalid embedding response");
            List<JsonNode> entries = new ArrayList<>();
            response.path("data").forEach(entries::add);
            entries.sort(Comparator.comparingInt(node -> node.path("index").asInt()));
            for (int i = 0; i < entries.size(); i++) {
                JsonNode entry = entries.get(i);
                if (entry.path("index").asInt(-1) != i || !entry.path("embedding").isArray())
                    throw new IllegalStateException("Invalid embedding index");
                JsonNode vector = entry.path("embedding");
                if (vector.size() == 0) throw new IllegalStateException("Empty embedding");
                double[] values = new double[vector.size()];
                for (int j = 0; j < values.length; j++) {
                    if (!vector.get(j).isNumber()) throw new IllegalStateException("Invalid embedding value");
                    values[j] = vector.get(j).asDouble();
                    if (!Double.isFinite(values[j])) throw new IllegalStateException("Non-finite embedding value");
                }
                result.add(values);
            }
        }
        return result;
    }
}
