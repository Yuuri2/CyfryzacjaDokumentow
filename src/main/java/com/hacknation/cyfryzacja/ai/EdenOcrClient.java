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
        String text = extractedText.length() > 30000
                ? extractedText.substring(0, 30000)
                : extractedText;

        String prompt = """
                Przeanalizuj poniższy tekst formularza urzędowego i zwróć wyłącznie poprawny obiekt JSON
                zgodny ze strukturą e-formularza COI.

                Wymagania:
                - title to tytuł usługi, maksymalnie 255 znaków
                - tabs musi mieć co najmniej dwie strony
                - pierwsza i pozostałe strony poza FINISH mają type MAIN
                - ostatnia strona ma type FINISH i może zawierać tylko INFO_SECTION
                - każda strona MAIN musi mieć co najmniej jedno pole
                - pola należy umieszczać w sekcjach SECTION
                - typy pól: TEXT_FIELD, TEXTAREA, NUMBER, CHECKBOX, RADIO, SELECT,
                  DATEPICKER, FILE_UPLOADER, CONSENT_SECTION, TEXT_BLOCK, INFO_SECTION, SECTION
                - validation: REQUIRED, EMAIL, PESEL, NIP, REGON, ZIP_CODE, EPUAP,
                  ADE, SERIAL_NUMBER_ID_CARD
                - flow.links musi łączyć strony w kolejności ich przejścia
                - wszystkie identyfikatory id, w tym UUID pól i stron, muszą być unikalne

                Zwróć tylko JSON bez bloków markdown i bez dodatkowego tekstu.

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

        JsonNode response = client.post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        if (response == null)
            throw new IllegalStateException("Eden AI zwróciło pustą odpowiedź");

        String content = response.path("choices").path(0).path("message").path("content").asText();
        if (content.isBlank())
            throw new IllegalStateException("Eden AI nie zwróciło JSON konfiguracji formularza");

        return content.trim();
    }
}
