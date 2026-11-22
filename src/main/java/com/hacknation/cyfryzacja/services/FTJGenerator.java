package com.hacknation.cyfryzacja.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hacknation.cyfryzacja.JSONRes.FormDefinition;
import com.hacknation.cyfryzacja.ai.EdenOcrClient;
import com.hacknation.cyfryzacja.parsers.DocumentParser;
import com.hacknation.cyfryzacja.walidacja.FileValidationService.ValidatedFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

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
            if (!root.isObject()) {
                throw new IllegalStateException("Eden AI zwróciło JSON nie będący obiektem");
            }
            ObjectNode normalized = normalize((ObjectNode) root);
            return objectMapper.convertValue(normalized, FormDefinition.class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Eden AI zwróciło JSON, którego nie udało się przetworzyć", e);
        }
    }

    private ObjectNode normalize(ObjectNode root) {
        if (!root.hasNonNull("title") || root.path("title").asText().isBlank()) {
            root.put("title", "Cyfryzacja formularza");
        }

        ArrayNode tabs;
        if (root.has("tabs") && root.get("tabs").isArray()) {
            tabs = (ArrayNode) root.get("tabs");
        } else {
            tabs = root.putArray("tabs");
        }
        if (tabs.isEmpty()) {
            ObjectNode finish = tabs.addObject();
            finish.put("id", UUID.randomUUID().toString());
            finish.put("title", "Strona końcowa");
            finish.put("type", "FINISH");
            finish.putArray("questions");
        }

        for (int i = 0; i < tabs.size(); i++) {
            ObjectNode tab = (ObjectNode) tabs.get(i);
            if (!tab.hasNonNull("id") || tab.path("id").asText().isBlank()) {
                tab.put("id", UUID.randomUUID().toString());
            }
            if (!tab.hasNonNull("title") || tab.path("title").asText().isBlank()) {
                tab.put("title", i == tabs.size() - 1 ? "Strona końcowa" : "Strona " + (i + 1));
            }
            if (!tab.hasNonNull("type")) {
                tab.put("type", i == tabs.size() - 1 ? "FINISH" : "MAIN");
            }
            tab.put("type", i == tabs.size() - 1 ? "FINISH" : "MAIN");

            ArrayNode questions;
            if (tab.has("questions") && tab.get("questions").isArray()) {
                questions = (ArrayNode) tab.get("questions");
            } else {
                questions = tab.putArray("questions");
            }
            for (int q = 0; q < questions.size(); q++) {
                ObjectNode question = (ObjectNode) questions.get(q);
                if (!question.has("x")) question.put("x", 0);
                if (!question.has("y")) question.put("y", q);
                if (!question.has("rows")) question.put("rows", 1);
                if (!question.has("cols")) question.put("cols", 3);
                if (question.has("field") && question.get("field").isObject()) {
                    normalizeField((ObjectNode) question.get("field"), q);
                } else {
                    ObjectNode field = question.putObject("field");
                    normalizeField(field, q);
                }
            }
        }

        ObjectNode flow;
        if (root.has("flow") && root.get("flow").isObject()) {
            flow = (ObjectNode) root.get("flow");
        } else {
            flow = root.putObject("flow");
        }
        ArrayNode links;
        if (flow.has("links") && flow.get("links").isArray()) {
            links = (ArrayNode) flow.get("links");
        } else {
            links = flow.putArray("links");
        }
        links.removeAll();
        for (int i = 0; i < tabs.size() - 1; i++) {
            ObjectNode link = links.addObject();
            link.put("id", UUID.randomUUID().toString());
            link.put("source", tabs.get(i).path("id").asText());
            link.put("target", tabs.get(i + 1).path("id").asText());
        }
        return root;
    }

    private void normalizeField(ObjectNode field, int order) {
        if (!field.hasNonNull("id") || field.path("id").asText().isBlank()) {
            field.put("id", UUID.randomUUID().toString());
        }
        if (!field.hasNonNull("type") || field.path("type").asText().isBlank()) {
            field.put("type", "TEXT_FIELD");
        }
        if (!field.hasNonNull("title") || field.path("title").asText().isBlank()) {
            field.put("title", "Pole " + (order + 1));
        }
        if (!field.hasNonNull("techName") || field.path("techName").asText().isBlank()) {
            field.put("techName", toTechName(field.path("title").asText()));
        }
        if (!field.has("description")) field.put("description", "");
        if (!field.has("options")) field.putArray("options");
        if (!field.has("validation")) field.putArray("validation");
        if (!field.has("readOnly")) field.put("readOnly", false);
        if (!field.has("states")) field.putArray("states");
        if (!field.has("repeatingSectionGrid")) field.putArray("repeatingSectionGrid");
        if (!field.has("questionOrder")) field.put("questionOrder", order);
        if (!field.has("tooltipMessage")) field.put("tooltipMessage", "");

        ArrayNode children = (ArrayNode) field.get("repeatingSectionGrid");
        for (int i = 0; i < children.size(); i++) {
            JsonNode child = children.get(i);
            if (!child.isObject()) continue;
            ObjectNode childQuestion = (ObjectNode) child;
            if (!childQuestion.has("x")) childQuestion.put("x", 0);
            if (!childQuestion.has("y")) childQuestion.put("y", i);
            if (!childQuestion.has("rows")) childQuestion.put("rows", 1);
            if (!childQuestion.has("cols")) childQuestion.put("cols", 3);
            if (childQuestion.has("field") && childQuestion.get("field").isObject()) {
                normalizeField((ObjectNode) childQuestion.get("field"), i);
            }
        }
    }

    private String toTechName(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.forLanguageTag("pl"));
        return normalized.replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
    }
}
