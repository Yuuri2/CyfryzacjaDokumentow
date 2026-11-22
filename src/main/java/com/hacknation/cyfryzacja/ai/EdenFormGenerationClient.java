package com.hacknation.cyfryzacja.ai;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(EdenFormGenerationClient.class);
    private final RestClient client;
    private final String model;
    private final DictionaryCatalog dictionaryCatalog;
    private final InitialDataCatalog initialDataCatalog;

    public EdenFormGenerationClient(
            @Value("${EDENAI_BASE_URL:https://api.edenai.run/v3}") String baseUrl,
            @Value("${EDENAI_API_KEY:}") String apiKey,
            @Value("${app.ai.eden-model:ovhcloud/Qwen3.5-9B}") String model,
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
        return generate(filename, extractedText, List.of());
    }

    @Override
    public String regenerateFormDefinition(String filename, String extractedText, List<String> violations) {
        return generate(filename, extractedText, violations);
    }

    private String generate(String filename, String extractedText, List<String> validationIssues) {
        List<Map<String, String>> messages = FormGenerationPrompt.messages(filename, extractedText,
                dictionaryCatalog.promptText() + initialDataCatalog.promptText(), validationIssues);
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", messages,
                "response_format", Map.of("type", "json_object"),
                // Qwen-compatible providers do not all honor chat_template_kwargs.
                // Send the documented direct switch as well, so OVH/Eden can disable
                // thinking and leave the completion budget for the actual JSON.
                "enable_thinking", false,
                "chat_template_kwargs", Map.of("enable_thinking", false),
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
                // Retrying server errors can duplicate a costly generation; only retry
                // rate limits, where the provider rejected the request before inference.
                if (status != 429) throw e;
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
        JsonNode choice = response.path("choices").path(0);
        JsonNode message = choice.path("message");
        String content = message.path("content").asText();
        String finishReason = choice.path("finish_reason").asText("unknown");
        int reasoningChars = message.path("reasoning_content").asText().length();
        log.info("Eden completion received: model={}, finishReason={}, contentChars={}, reasoningChars={}",
                model, finishReason, content.length(), reasoningChars);
        if (content.isBlank()) {
            throw new IllegalStateException("Model Eden AI zwrócił pustą treść (finish_reason="
                    + finishReason + ", reasoning_chars=" + reasoningChars
                    + "). Spróbuj ponownie po wyłączeniu trybu reasoning po stronie dostawcy.");
        }
        return content.trim();
    }
}
