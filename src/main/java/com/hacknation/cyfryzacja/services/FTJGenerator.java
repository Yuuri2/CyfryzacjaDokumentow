package com.hacknation.cyfryzacja.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hacknation.cyfryzacja.JSONRes.FormDefinition;
import com.hacknation.cyfryzacja.ai.EdenOcrClient;
import com.hacknation.cyfryzacja.parsers.DocumentParser;
import com.hacknation.cyfryzacja.walidacja.FileValidationService.ValidatedFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class FTJGenerator {
    private final EdenOcrClient edenOcrClient;
    private final DocumentParser documentParser;
    private final ObjectMapper objectMapper;

    public FTJGenerator(EdenOcrClient edenOcrClient, DocumentParser documentParser, ObjectMapper objectMapper) {
        this.edenOcrClient = edenOcrClient;
        this.documentParser = documentParser;
        this.objectMapper = objectMapper;
    }

    public FormDefinition generate(ValidatedFile validatedFile, MultipartFile file) {
        String extractedText;
        if (validatedFile.mimeType().startsWith("image/")) {
            extractedText = edenOcrClient.extractText(file);
        } else {
            extractedText = documentParser.parse(file);
        }

        String jsonText = edenOcrClient.generateFormDefinition(
                validatedFile.filename(),
                extractedText
        );

        try {
            JsonNode root = objectMapper.readTree(jsonText);
            return objectMapper.convertValue(root, FormDefinition.class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Eden AI zwróciło JSON, którego nie udało się przetworzyć", e);
        }
    }
}
