package com.hacknation.cyfryzacja.ai;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.hacknation.cyfryzacja.services.DictionaryCatalog;
import com.hacknation.cyfryzacja.services.InitialDataCatalog;

import tools.jackson.databind.JsonNode;

/** Eden adapter for the same Qwen3.5-9B model used by the local Ollama adapter. */
@Service
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "eden", matchIfMissing = true)
public class EdenFormGenerationClient implements FormGenerationClient {
    private final RestClient client;
    private final String model;
    private final DictionaryCatalog dictionaryCatalog;
    private final InitialDataCatalog initialDataCatalog;

    public EdenFormGenerationClient(
            @Value("${EDENAI_BASE_URL:https://api.edenai.run/v3}") String baseUrl,
            @Value("${EDENAI_API_KEY:}") String apiKey,
            @Value("${app.ai.eden-model:ionos/Qwen/Qwen3.5-9B}") String model,
            DictionaryCatalog dictionaryCatalog,
            InitialDataCatalog initialDataCatalog) {
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
        if (!apiKey.isBlank()) builder.defaultHeader("Authorization", "Bearer " + apiKey);
        this.client = builder.build();
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
                "response_format", Map.of("type", "json_object"),
                "temperature", 0);

        JsonNode response = null;
        RestClientResponseException lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                response = client.post().uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(JsonNode.class);
                break;
            } catch (RestClientResponseException e) {
                lastException = e;
                int status = e.getStatusCode().value();
                if (status != 429 && status != 502 && status != 503 && status != 504) throw e;
                if (attempt < 3) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Przerwano oczekiwanie na ponowienie generowania", interrupted);
                    }
                }
            }
        }
        if (response == null) {
            throw new IllegalStateException("Model Eden AI chwilowo nie odpowiada (HTTP "
                    + (lastException == null ? "brak odpowiedzi" : lastException.getStatusCode().value()) + ").", lastException);
        }
        String content = response.path("choices").path(0).path("message").path("content").asText();
        if (content.isBlank()) throw new IllegalStateException("Model Eden AI nie zwrócił konfiguracji formularza.");
        return content.trim();
    }
}
