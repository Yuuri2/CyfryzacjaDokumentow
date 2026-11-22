package com.hacknation.cyfryzacja.ai;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.hacknation.cyfryzacja.services.DictionaryCatalog;
import com.hacknation.cyfryzacja.services.InitialDataCatalog;

import tools.jackson.databind.JsonNode;

/** Local Ollama adapter for the Qwen3.5-9B model; no Eden or other remote AI endpoint is used. */
@Service
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "local")
public class LocalQwenFormGenerationClient implements FormGenerationClient {
    private final RestClient client;
    private final String model;
    private final DictionaryCatalog dictionaryCatalog;
    private final InitialDataCatalog initialDataCatalog;

    public LocalQwenFormGenerationClient(
            @Value("${app.ai.local-base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.ai.local-model:qwen3.5:9b}") String model,
            DictionaryCatalog dictionaryCatalog,
            InitialDataCatalog initialDataCatalog) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.model = model;
        this.dictionaryCatalog = dictionaryCatalog;
        this.initialDataCatalog = initialDataCatalog;
    }

    @Override
    public String generateFormDefinition(String filename, String extractedText) {
        List<Map<String, String>> messages = FormGenerationPrompt.messages(filename, extractedText,
                dictionaryCatalog.promptText() + initialDataCatalog.promptText());
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", messages,
                "stream", false,
                "format", "json",
                "options", Map.of("temperature", 0, "num_ctx", 32768, "num_predict", 8192));
        final JsonNode response;
        try {
            response = client.post().uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Lokalny model Qwen3.5-9B jest niedostępny. Sprawdź Ollama i pobrany model qwen3.5:9b.", e);
        }
        String content = response == null ? "" : response.path("message").path("content").asText();
        if (content.isBlank()) throw new IllegalStateException("Lokalny model Qwen3.5-9B nie zwrócił konfiguracji formularza.");
        return content.trim();
    }
}
