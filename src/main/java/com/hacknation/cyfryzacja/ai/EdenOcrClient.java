package com.hacknation.cyfryzacja.ai;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

@Service
public class EdenOcrClient {
    private static final String OCR_MODEL = "ocr/ocr/mistral/mistral-ocr-4-1";
    private final RestClient client;
    private final String defaultModel;

    public EdenOcrClient(@Value("${EDENAI_BASE_URL:https://api.edenai.run/v3}") String baseUrl,
                         @Value("${EDENAI_API_KEY:}") String apiKey,
                         @Value("${app.edenai.default-model:google/gemma-4-31b-it}") String defaultModel) {
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
        if (!apiKey.isBlank()) builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        this.client = builder.build();
        this.defaultModel = defaultModel;
    }

    private String uploadFile(MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Nie można odczytać zawartości pliku", e);
        }

        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(
                file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, fileHeaders));

        JsonNode response = client.post().uri("/upload").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body).retrieve().body(JsonNode.class);
        if (response == null || !response.hasNonNull("file_id"))
            throw new IllegalStateException("Nie otrzymano file_id z Eden AI");
        return response.get("file_id").asText();
    }

    public JsonNode performOcr(String fileId) {
        Map<String, Object> request = Map.of("model", OCR_MODEL, "input", Map.of("file", fileId));
        JsonNode response = client.post().uri("/universal-ai").contentType(MediaType.APPLICATION_JSON)
                .body(request).retrieve().body(JsonNode.class);
        if (response == null) throw new IllegalStateException("Eden AI zwróciło pustą odpowiedź OCR");
        return response;
    }

    public String extractText(MultipartFile file) {
        if (file == null) throw new IllegalArgumentException("Plik nie może być null");
        String fileId = uploadFile(file);
        JsonNode response = performOcr(fileId);
        if (!"success".equals(response.path("status").asText()))
            throw new IllegalStateException("Błąd OCR Eden AI: " + response.path("error").asText());
        String extractedText = response.path("output").path("text").asText();
        if (extractedText.isBlank()) throw new IllegalStateException("Eden AI nie zwróciło tekstu OCR");
        return extractedText;
    }

    public String generateFormDefinition(String filename, String extractedText) {
        String text = extractedText.length() > 60000
                ? extractedText.substring(0, 60000)
                : extractedText;

        String prompt = """
                Przeanalizuj poniższy formularz urzędowy i zwróć wyłącznie jeden obiekt JSON.
                JSON musi odzwierciedlać treść formularza, a nie tylko tytuł i nazwy stron.

                Format wynikowy:
                {
                  "title": "pełny tytuł usługi",
                  "tabs": [
                    {
                      "id": "UUID",
                      "title": "tematyczna nazwa strony",
                      "type": "MAIN",
                      "questions": [
                        {
                          "x": 0,
                          "y": 0,
                          "rows": 2,
                          "cols": 3,
                          "field": {
                            "id": "UUID",
                            "type": "SECTION",
                            "techName": "nazwa_sekcji_po_snake_case",
                            "title": "Nazwa sekcji",
                            "description": "<html><head></head><body></body></html>",
                            "options": [],
                            "validation": [],
                            "readOnly": false,
                            "repeatingSectionGrid": [
                              {
                                "x": 0,
                                "y": 0,
                                "rows": 1,
                                "cols": 3,
                                "field": {
                                  "id": "UUID",
                                  "type": "TEXT_FIELD",
                                  "techName": "nazwisko",
                                  "title": "Nazwisko",
                                  "description": "",
                                  "options": [],
                                  "validation": ["REQUIRED"],
                                  "readOnly": false,
                                  "repeatingSectionGrid": [],
                                  "states": [],
                                  "questionOrder": 0,
                                  "tooltipMessage": ""
                                }
                              }
                            ],
                            "states": [],
                            "questionOrder": 0,
                            "tooltipMessage": ""
                          }
                        }
                      ]
                    },
                    {
                      "id": "UUID",
                      "title": "Strona końcowa",
                      "type": "FINISH",
                      "questions": []
                    }
                  ],
                  "flow": {
                    "links": [
                      {"id":"UUID","source":"UUID pierwszej strony","target":"UUID drugiej strony"}
                    ]
                  }
                }

                Wymagania szczegółowe:
                - title nie może być pusty i może mieć maksymalnie 255 znaków.
                - tabs musi mieć co najmniej dwie strony; ostatnia strona musi mieć type FINISH.
                - strony MAIN muszą mieć tematyczne title i questions zawierające wszystkie sekcje, pola i dane z formularza.
                - Każdą sekcję reprezentuj jako SECTION z polami w repeatingSectionGrid.
                - Teksty pól, pola wyboru, checkboxy, radio, listy, daty, adresy, załączniki i obowiązkowe dane muszą zostać przeniesione do JSON.
                - Dla pól wybieranych użyj RADIO, SELECT lub CHECKBOX i dokładnie przenieś ich opcje jako name, techName i id.
                - Do pól tekstowych użyj TEXT_FIELD lub TEXTAREA.
                - Do liczb użyj NUMBER, dat DATEPICKER, plików FILE_UPLOADER, zgód CONSENT_SECTION.
                - legalne validation: REQUIRED, EMAIL, PESEL, NIP, REGON, ZIP_CODE, EPUAP, ADE, SERIAL_NUMBER_ID_CARD.
                - flow.links musi zawierać dokładnie po jednym połączeniu z każdej strony do następnej strony.
                - Każde id strony, pola i linku musi być unikalnym UUID.
                - Nie pomijaj sections i nested fields.
                - Nie zwracaj tylko title/tabs/flow. Zwróć kompletne pytania dla treści formularza.
                - Zwróć wyłącznie poprawny JSON bez bloków markdown i bez dodatkowego tekstu.

                Nazwa pliku: %s

                Treść formularza:
                %s
                """.formatted(filename, text);

        Map<String, Object> request = Map.of(
                "model", defaultModel,
                "messages", List.of(
                        Map.of("role", "system", "content", "Jesteś ekspertem od cyfryzacji formularzy administracji publicznej."),
                        Map.of("role", "user", "content", prompt)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0
        );

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
                if (status != 429 && status != 502 && status != 503 && status != 504) {
                    throw e;
                }
                if (attempt < 3) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Przerwano oczekiwanie na ponowienie Eden AI", interrupted);
                    }
                }
            }
        }
        if (response == null) {
            throw new IllegalStateException(
                    "Eden AI chwilowo ogranicza żądania lub jest niedostępne (HTTP "
                            + (lastException != null ? lastException.getStatusCode().value() : "brak odpowiedzi")
                            + "). Spróbuj ponownie za chwilę.",
                    lastException);
        }

        if (response == null)
            throw new IllegalStateException("Eden AI zwróciło pustą odpowiedź");

        String content = response.path("choices").path(0).path("message").path("content").asText();
        if (content.isBlank())
            throw new IllegalStateException("Eden AI nie zwróciło JSON konfiguracji formularza");

        return content.trim();
    }
}
