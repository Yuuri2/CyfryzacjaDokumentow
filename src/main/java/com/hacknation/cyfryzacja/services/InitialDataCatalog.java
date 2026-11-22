package com.hacknation.cyfryzacja.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** COI initial-data source and attribute allow-list from the challenge handout. */
@Component
public class InitialDataCatalog {
    private static final Pattern REFERENCE = Pattern.compile("([A-Z][A-Z0-9_]*)\\.([A-Za-z][A-Za-z0-9]*)");
    private final JsonNode catalog;
    private final Map<String, String> attributeTypes = new HashMap<>();

    public InitialDataCatalog(ObjectMapper mapper) {
        try (var input = InitialDataCatalog.class.getResourceAsStream("/initial-data-catalog.json")) {
            if (input == null) throw new IllegalStateException("Brak initial-data-catalog.json");
            catalog = mapper.readTree(input);
            for (JsonNode source : catalog.path("sources")) {
                String sourceName = source.path("name").asText();
                for (JsonNode attribute : source.path("attributes")) {
                    attributeTypes.put(sourceName + "." + attribute.path("name").asText(),
                            attribute.path("fieldType").asText());
                }
            }
        } catch (java.io.IOException | JacksonException e) {
            throw new IllegalStateException("Nie można wczytać katalogu danych inicjalnych", e);
        }
    }

    public JsonNode publicCatalog() {
        return catalog;
    }

    public String promptText() {
        StringBuilder out = new StringBuilder("Źródła danych inicjalnych COI dozwolone w defaultValue (wymagane readOnly=true i defaultValueType=EXTERNAL_SYSTEM):\n");
        for (JsonNode source : catalog.path("sources")) {
            out.append("- ").append(source.path("name").asText()).append(" / ")
                    .append(source.path("displayName").asText()).append(": ");
            for (int i = 0; i < source.path("attributes").size(); i++) {
                JsonNode attr = source.path("attributes").get(i);
                if (i > 0) out.append(", ");
                out.append(source.path("name").asText()).append('.').append(attr.path("name").asText())
                        .append(" (").append(attr.path("fieldType").asText()).append(')');
            }
            out.append('\n');
        }
        out.append("Użyj tych wartości tylko wtedy, gdy materiał uzasadnia autouzupełnianie. Nie wymyślaj atrybutów.\n");
        out.append("Do walidatorów używaj wyłącznie: REQUIRED, EMAIL, PESEL, NIP, REGON, ZIP_CODE, EPUAP, ADE, SERIAL_NUMBER_ID_CARD.\n");
        return out.toString();
    }

    public List<String> validate(JsonNode root) {
        List<String> errors = new ArrayList<>();
        JsonNode tabs = root == null ? null : root.get("tabs");
        if (tabs == null || !tabs.isArray()) return errors;
        for (int i = 0; i < tabs.size(); i++) {
            validateQuestions(tabs.get(i).path("questions"), "$.tabs[" + i + "].questions", errors);
        }
        return List.copyOf(errors);
    }

    private void validateQuestions(JsonNode questions, String path, List<String> errors) {
        if (questions == null || !questions.isArray()) return;
        for (int i = 0; i < questions.size(); i++) {
            JsonNode field = questions.get(i).path("field");
            String fieldPath = path + "[" + i + "].field";
            boolean hasDefaultValue = field.has("defaultValue") && !field.get("defaultValue").isNull();
            boolean hasDefaultType = field.has("defaultValueType") && !field.get("defaultValueType").isNull();
            if (hasDefaultValue && !hasDefaultType) {
                errors.add(fieldPath + ".defaultValueType: wymagane EXTERNAL_SYSTEM dla wartości źródłowej");
            }
            if (hasDefaultType && !hasDefaultValue) {
                errors.add(fieldPath + ".defaultValue: wymagane SOURCE.attribute dla EXTERNAL_SYSTEM");
            }
            String type = field.path("defaultValueType").asText();
            if (type.isBlank() || "null".equals(type)) {
                if (field.has("defaultValueType") && !field.get("defaultValueType").isNull()) {
                    errors.add(fieldPath + ".defaultValueType: dozwolone jest wyłącznie EXTERNAL_SYSTEM lub brak wartości");
                }
            } else if (!"EXTERNAL_SYSTEM".equals(type)) {
                errors.add(fieldPath + ".defaultValueType: dozwolona wartość to EXTERNAL_SYSTEM");
            } else {
                String reference = field.path("defaultValue").asText();
                var matcher = REFERENCE.matcher(reference);
                if (!matcher.matches() || !attributeTypes.containsKey(reference)) {
                    errors.add(fieldPath + ".defaultValue: źródło lub atrybut nie występuje w danych inicjalnych COI");
                } else {
                    String expectedType = attributeTypes.get(reference);
                    if (!expectedType.equals(field.path("type").asText())) {
                        errors.add(fieldPath + ".type: atrybut " + reference + " wymaga typu " + expectedType);
                    }
                    if (!field.path("readOnly").asBoolean(false)) {
                        errors.add(fieldPath + ".readOnly: pole zasilane z rejestru musi być tylko do odczytu");
                    }
                }
            }
            validateQuestions(field.path("repeatingSectionGrid"), fieldPath + ".repeatingSectionGrid", errors);
        }
    }
}
