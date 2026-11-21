//JsonNode służy tutaj wyłącznie do odebrania odpowiedzi technicznej API. Nie prosimy AI o generowanie JSON-a formularza.

package com.hacknation.cyfryzacja.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

public class EdenOcrClient {

    private static final String BASE_URL =
            "https://api.edenai.run/v3";

    private static final String OCR_MODEL =
            "ocr/ocr/mistral/mistral-ocr-4-0";

    private final RestClient client;

    public EdenOcrClient(
            @Value("${edenai.api-key}") String apiKey
    ) {
        this.client = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + apiKey
                )
                .build();
    }

    // 1. Upload dokumentu do Eden AI
    private String uploadFile(MultipartFile file)
            throws IOException {

        ByteArrayResource resource =
                new ByteArrayResource(file.getBytes()) {

                    @Override
                    public String getFilename() {
                        return file.getOriginalFilename() != null
                                ? file.getOriginalFilename()
                                : "document";
                    }
                };

        HttpHeaders fileHeaders = new HttpHeaders();

        fileHeaders.setContentType(
                MediaType.APPLICATION_OCTET_STREAM
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
                    "Nie otrzymano file_id z Eden AI: "
                            + response
            );
        }

        return response.get("file_id").asText();
    }

    // 2. Wykonanie OCR
    private JsonNode performOcr(String fileId) {

        Map<String, Object> request = Map.of(
                "model", OCR_MODEL,
                "input", Map.of(
                        "file", fileId
                )
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
    // 3. Główna metoda ekstrakcji
    public JsonNode extract(MultipartFile file)
            throws IOException {

        String fileId = uploadFile(file);

        return performOcr(fileId);
    }
}
