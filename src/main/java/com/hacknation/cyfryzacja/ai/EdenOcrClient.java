
package com.hacknation.cyfryzacja.ai;

import tools.jackson.databind.JsonNode;
import com.hacknation.cyfryzacja.walidacja.FileValidationServiece;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class EdenOcrClient {

    private static final String OCR_MODEL =
            "ocr/ocr/mistral/mistral-ocr-4-1";

    private final RestClient client;

    public EdenOcrClient(
            @Value("${EDENAI_BASE_URL:https://api.edenai.run/v3}") String baseUrl,
            @Value("${EDENAI_API_KEY:}") String apiKey
    ) {
        RestClient.Builder builder =
                RestClient.builder().baseUrl(baseUrl);

        if (!apiKey.isBlank()) {
            builder.defaultHeader(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + apiKey
            );
        }

        this.client = builder.build();
    }

    // 1. Upload pliku do Eden AI
    private String uploadFile(
            FileValidationServiece.ValidatedFile file
    ) {

        ByteArrayResource resource =
                new ByteArrayResource(file.content()) {

                    @Override
                    public String getFilename() {
                        return file.filename();
                    }
                };

        HttpHeaders fileHeaders = new HttpHeaders();

        fileHeaders.setContentType(
                MediaType.parseMediaType(file.mimeType())
        );

        MultiValueMap<String, Object> body =
                new LinkedMultiValueMap<>();

        body.add(
                "file",
                new HttpEntity<>(resource, fileHeaders)
        );

        JsonNode response = client.post()
                .uri("/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null ||
                !response.hasNonNull("file_id")) {

            throw new IllegalStateException(
                    "Nie otrzymano file_id z Eden AI"
            );
        }

        return response.get("file_id").asText();
    }

    // 2. Wykonanie OCR
    private JsonNode performOcr(String fileId) {

        Map<String, Object> request = Map.of(
                "model", OCR_MODEL,
                "input", Map.of("file", fileId)
        );

        JsonNode response = client.post()
                .uri("/universal-ai")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException(
                    "Eden AI zwróciło pustą odpowiedź OCR"
            );
        }

        return response;
    }

    // 3. Główna metoda ekstrakcji tekstu
    public String extractText(
            FileValidationServiece.ValidatedFile file
    ) {

        if (file == null) {
            throw new IllegalArgumentException(
                    "Plik nie może być null"
            );
        }

        String fileId = uploadFile(file);

        JsonNode response = performOcr(fileId);

        if (!"success".equals(
                response.path("status").asText())) {

            throw new IllegalStateException(
                    "Błąd OCR Eden AI: "
                            + response.path("error")
            );
        }

        String extractedText = response
                .path("output")
                .path("text")
                .asText();

        if (extractedText.isBlank()) {
            throw new IllegalStateException(
                    "Eden AI nie zwróciło tekstu OCR"
            );
        }

        return extractedText;
    }
}
