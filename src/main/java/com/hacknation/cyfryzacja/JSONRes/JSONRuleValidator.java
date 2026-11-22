package com.hacknation.cyfryzacja.JSONRes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Validation against the supplied COI schema and the technical rules in the challenge package. */
@Component
public class JSONRuleValidator {
    private static final int MAX_TITLE_LENGTH = 255;
    private static final Pattern TECH_NAME = Pattern.compile("[a-z][a-z0-9_]{0,99}");
    private static final Pattern CONDITION_TECH_NAME = Pattern.compile("\\['([^']+)'\\s+in\\s+field\\.techName\\]");
    private static final Set<String> TAB_TYPES = Set.of("MAIN", "FINISH");
    private static final Set<String> FIELD_TYPES = Set.of("TEXT_FIELD", "CHECKBOX", "NUMBER", "CONSENT_SECTION",
            "SELECT", "TEXTAREA", "FILE_UPLOADER", "RADIO", "DATEPICKER", "TEXT_BLOCK", "INFO_SECTION",
            "SECTION", "REPEATING_SECTION");
    private static final Set<String> INPUT_TYPES = Set.of("TEXT_FIELD", "CHECKBOX", "NUMBER", "SELECT", "TEXTAREA",
            "FILE_UPLOADER", "RADIO", "DATEPICKER", "CONSENT_SECTION");
    private static final Set<String> VALIDATORS = Set.of("REQUIRED", "EMAIL", "PESEL", "NIP", "REGON", "ZIP_CODE",
            "EPUAP", "ADE", "SERIAL_NUMBER_ID_CARD");
    private static final Set<String> DICTIONARIES = Set.of("Kraje", "Województwo", "Powiat", "Gmina", "Miejscowość",
            "Numery kierunkowe", "TERYT_DOWODOWY", "TERYT_4G_DOWODOWY");

    private final JsonNode officialSchema;

    public JSONRuleValidator(ObjectMapper objectMapper) {
        try (var input = JSONRuleValidator.class.getResourceAsStream("/coi-form-schema.json")) {
            if (input == null) throw new IllegalStateException("Brak zasobu coi-form-schema.json");
            officialSchema = objectMapper.readTree(input);
        } catch (java.io.IOException | JacksonException e) {
            throw new IllegalStateException("Nie można wczytać schematu importu COI", e);
        }
    }

    public List<String> validate(JsonNode root) {
        List<String> errors = new ArrayList<>();
        if (root == null || !root.isObject()) return List.of("$: oczekiwano obiektu JSON");
        if (root.has("generationIssues")) {
            JsonNode issues = root.get("generationIssues");
            if (issues.isArray() && !issues.isEmpty()) {
                for (int i = 0; i < issues.size(); i++) {
                    errors.add("$.generationIssues[" + i + "]: " + issues.get(i).asText("brak danych potrzebnych do wygenerowania formularza"));
                }
            } else {
                errors.add("$.generationIssues: model zgłosił niekompletny formularz");
            }
        }
        validateSchemaTypes(root, officialSchema, "$", errors);
        text(root, "title", "$.title", errors);
        if (root.path("title").asText().length() > MAX_TITLE_LENGTH) errors.add("$.title: maksymalna długość to 255 znaków");

        JsonNode tabs = root.get("tabs");
        if (tabs == null || !tabs.isArray() || tabs.size() < 2) {
            errors.add("$.tabs: wymagane są co najmniej dwie strony");
            return List.copyOf(errors);
        }
        Set<String> allIds = new HashSet<>();
        Set<String> techNames = new HashSet<>();
        collectTechNames(tabs, techNames);
        for (int i = 0; i < tabs.size(); i++) {
            JsonNode tab = tabs.get(i);
            String path = "$.tabs[" + i + "]";
            if (!tab.isObject()) { errors.add(path + ": oczekiwano obiektu strony"); continue; }
            uuid(tab, "id", path + ".id", allIds, errors);
            text(tab, "title", path + ".title", errors);
            String type = tab.path("type").asText();
            if (!TAB_TYPES.contains(type)) errors.add(path + ".type: dozwolone wartości to MAIN i FINISH");
            if (i == tabs.size() - 1 && !"FINISH".equals(type)) errors.add(path + ".type: ostatnia strona musi być FINISH");
            if (i < tabs.size() - 1 && !"MAIN".equals(type)) errors.add(path + ".type: tylko ostatnia strona może być FINISH");
            JsonNode questions = tab.get("questions");
            if (questions == null || !questions.isArray() || questions.isEmpty()) {
                errors.add(path + ".questions: każda strona musi zawierać co najmniej jedno pole");
            } else {
                validateQuestions(questions, path + ".questions", allIds, techNames, false,
                        "FINISH".equals(type), errors);
            }
        }
        validateFlow(root.get("flow"), tabs, errors);
        validateDictionaryProposals(root.get("dictionaryProposals"), errors);
        return List.copyOf(errors);
    }

    private void validateQuestions(JsonNode questions, String path, Set<String> ids, Set<String> techNames,
                                   boolean insideContainer, boolean finishPage, List<String> errors) {
        for (int i = 0; i < questions.size(); i++) {
            JsonNode question = questions.get(i);
            String qp = path + "[" + i + "]";
            if (!question.isObject()) { errors.add(qp + ": oczekiwano obiektu pytania"); continue; }
            nonNegativeInt(question, "x", qp, errors);
            nonNegativeInt(question, "y", qp, errors);
            positiveInt(question, "rows", qp, errors);
            positiveInt(question, "cols", qp, errors);
            JsonNode field = question.get("field");
            if (field == null || !field.isObject()) { errors.add(qp + ".field: wymagany jest obiekt pola"); continue; }
            validateField(field, qp + ".field", ids, techNames, insideContainer, finishPage, errors);
        }
    }

    private void validateField(JsonNode field, String path, Set<String> ids, Set<String> techNames,
                               boolean insideContainer, boolean finishPage, List<String> errors) {
        uuid(field, "id", path + ".id", ids, errors);
        String type = field.path("type").asText();
        if (!FIELD_TYPES.contains(type)) errors.add(path + ".type: typ nieobsługiwany przez reguły COI: '" + type + "'");
        boolean container = Set.of("SECTION", "REPEATING_SECTION").contains(type);
        boolean informational = Set.of("INFO_SECTION", "TEXT_BLOCK").contains(type);
        if (!insideContainer && !container && !(finishPage && informational)) {
            errors.add(path + ": pole musi należeć do SECTION lub REPEATING_SECTION");
        }
        if (insideContainer && container) errors.add(path + ": COI nie dopuszcza zagnieżdżania sekcji");
        if (finishPage && INPUT_TYPES.contains(type)) errors.add(path + ".type: FINISH nie może zawierać pól do wprowadzania danych");

        String techName = field.path("techName").asText();
        if (!TECH_NAME.matcher(techName).matches()) errors.add(path + ".techName: wymagana nazwa techniczna snake_case");
        if (Set.of("SECTION", "REPEATING_SECTION").contains(type)) text(field, "title", path + ".title", errors);
        if ("INFO_SECTION".equals(type)) text(field, "description", path + ".description", errors);
        optionalText(field, "title", path + ".title", errors);
        optionalText(field, "description", path + ".description", errors);
        optionalText(field, "tooltipMessage", path + ".tooltipMessage", errors);

        String dictionaryName = field.path("dictionaryName").asText();
        if (!dictionaryName.isBlank() && !DICTIONARIES.contains(dictionaryName)) errors.add(path + ".dictionaryName: słownika nie ma w materiałach COI");
        if (!dictionaryName.isBlank() && !Set.of("SELECT", "RADIO", "CHECKBOX").contains(type)) {
            errors.add(path + ".dictionaryName: słownik można użyć tylko w SELECT, RADIO lub CHECKBOX");
        }
        requireArray(field, "options", path, errors);
        requireArray(field, "validation", path, errors);
        requireArray(field, "states", path, errors);
        requireArray(field, "repeatingSectionGrid", path, errors);
        if (field.has("readOnly") && !field.get("readOnly").isBoolean()) errors.add(path + ".readOnly: oczekiwano wartości logicznej");
        if (field.has("questionOrder") && !field.get("questionOrder").isNumber()) errors.add(path + ".questionOrder: oczekiwano liczby");

        JsonNode options = field.get("options");
        if (options != null && options.isArray()) {
            for (int i = 0; i < options.size(); i++) {
                JsonNode option = options.get(i);
                String op = path + ".options[" + i + "]";
                if (!option.isObject()) { errors.add(op + ": oczekiwano obiektu opcji"); continue; }
                text(option, "id", op + ".id", errors);
                text(option, "name", op + ".name", errors);
                if (!TECH_NAME.matcher(option.path("techName").asText()).matches()) errors.add(op + ".techName: wymagana nazwa snake_case");
            }
        }
        if (Set.of("SELECT", "RADIO", "CHECKBOX").contains(type) && dictionaryName.isBlank()
                && (options == null || !options.isArray() || options.isEmpty())) {
            errors.add(path + ".options: pole wyboru wymaga opcji albo słownika");
        }
        validateValidators(field.get("validation"), path, errors);
        validateStates(field.get("states"), path, type, techName, techNames, errors);

        JsonNode children = field.get("repeatingSectionGrid");
        if (container && (children == null || !children.isArray() || children.isEmpty())) {
            errors.add(path + ".repeatingSectionGrid: sekcja musi zawierać pola");
        }
        if (children != null && children.isArray()) {
            validateQuestions(children, path + ".repeatingSectionGrid", ids, techNames, true, finishPage, errors);
        }
    }

    private void validateValidators(JsonNode validations, String path, List<String> errors) {
        if (validations == null || !validations.isArray()) return;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < validations.size(); i++) {
            String rule = validations.get(i).asText();
            if (!VALIDATORS.contains(rule)) errors.add(path + ".validation[" + i + "]: walidator spoza reguł technicznych COI: '" + rule + "'");
            if (!seen.add(rule)) errors.add(path + ".validation[" + i + "]: powtórzony walidator");
        }
    }

    private void validateStates(JsonNode states, String path, String fieldType, String techName,
                                Set<String> techNames, List<String> errors) {
        if (states == null || !states.isArray()) return;
        Set<String> allowed = Set.of("SECTION", "REPEATING_SECTION").contains(fieldType)
                ? Set.of("SHOW") : Set.of("SHOW", "READ_ONLY");
        for (int i = 0; i < states.size(); i++) {
            JsonNode state = states.get(i);
            String sp = path + ".states[" + i + "]";
            String type = state.path("type").asText();
            String query = state.path("query").asText();
            if (!allowed.contains(type)) errors.add(sp + ".type: dozwolone stany dla " + fieldType + " to " + String.join(", ", allowed));
            if (query.isBlank()) errors.add(sp + ".query: wymagany warunek JSONata");
            if (!query.isBlank() && !query.contains("__userAnswer")) errors.add(sp + ".query: warunek musi odwoływać się do odpowiedzi __userAnswer");
            Matcher names = CONDITION_TECH_NAME.matcher(query);
            int references = 0;
            while (names.find()) {
                references++;
                String reference = names.group(1);
                if (!techNames.contains(reference)) errors.add(sp + ".query: odwołanie do nieistniejącego pola '" + reference + "'");
                if (reference.equals(techName)) errors.add(sp + ".query: pole nie może sterować własnym stanem");
            }
            if (!query.isBlank() && references == 0) errors.add(sp + ".query: brak odwołania do pola sterującego w składni COI");
            if (!balanced(query, '(', ')') || !balanced(query, '[', ']')) errors.add(sp + ".query: niezbilansowane nawiasy JSONata");
        }
    }

    private void validateFlow(JsonNode flow, JsonNode tabs, List<String> errors) {
        JsonNode links = flow != null && flow.isObject() ? flow.get("links") : null;
        if (links == null || !links.isArray() || links.isEmpty()) { errors.add("$.flow.links: wymagane są przejścia stron"); return; }
        Map<String, Set<String>> graph = new HashMap<>();
        Map<String, Set<String>> reverse = new HashMap<>();
        Set<String> tabIds = new HashSet<>();
        for (JsonNode tab : tabs) {
            String id = tab.path("id").asText();
            tabIds.add(id); graph.put(id, new HashSet<>()); reverse.put(id, new HashSet<>());
        }
        Set<String> ids = new HashSet<>(), edges = new HashSet<>();
        for (int i = 0; i < links.size(); i++) {
            JsonNode link = links.get(i);
            String p = "$.flow.links[" + i + "]";
            if (!link.isObject()) { errors.add(p + ": oczekiwano obiektu"); continue; }
            uuid(link, "id", p + ".id", ids, errors);
            text(link, "source", p + ".source", errors);
            text(link, "target", p + ".target", errors);
            String source = link.path("source").asText(), target = link.path("target").asText();
            if (!tabIds.contains(source)) errors.add(p + ".source: nieznana strona");
            if (!tabIds.contains(target)) errors.add(p + ".target: nieznana strona");
            if (source.equals(target)) errors.add(p + ": przejście nie może wskazywać tej samej strony");
            if (!edges.add(source + "->" + target)) errors.add(p + ": powtórzone przejście");
            if (graph.containsKey(source) && graph.containsKey(target)) {
                graph.get(source).add(target); reverse.get(target).add(source);
            }
        }
        String start = tabs.get(0).path("id").asText();
        String finish = tabs.get(tabs.size() - 1).path("id").asText();
        if (hasCycle(graph)) errors.add("$.flow.links: przepływ stron nie może zawierać cyklu");
        Set<String> fromStart = reachable(start, graph);
        Set<String> toFinish = reachable(finish, reverse);
        for (int i = 0; i < tabs.size(); i++) {
            String id = tabs.get(i).path("id").asText();
            if (!fromStart.contains(id)) errors.add("$.tabs[" + i + "]: strona nieosiągalna od początku formularza");
            if (!toFinish.contains(id)) errors.add("$.tabs[" + i + "]: brak ścieżki do strony FINISH");
        }
    }

    private boolean hasCycle(Map<String, Set<String>> graph) {
        Set<String> visiting = new HashSet<>(), visited = new HashSet<>();
        for (String node : graph.keySet()) if (hasCycle(node, graph, visiting, visited)) return true;
        return false;
    }

    private boolean hasCycle(String node, Map<String, Set<String>> graph, Set<String> visiting, Set<String> visited) {
        if (visiting.contains(node)) return true;
        if (!visited.add(node)) return false;
        visiting.add(node);
        for (String next : graph.getOrDefault(node, Set.of())) if (hasCycle(next, graph, visiting, visited)) return true;
        visiting.remove(node);
        return false;
    }

    private Set<String> reachable(String start, Map<String, Set<String>> graph) {
        Set<String> found = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (found.add(current)) queue.addAll(graph.getOrDefault(current, Set.of()));
        }
        return found;
    }

    private boolean balanced(String text, char open, char close) {
        int depth = 0;
        for (char c : text.toCharArray()) {
            if (c == open) depth++;
            if (c == close && --depth < 0) return false;
        }
        return depth == 0;
    }

    private void validateDictionaryProposals(JsonNode proposals, List<String> errors) {
        if (proposals == null) return;
        if (!proposals.isArray()) { errors.add("$.dictionaryProposals: oczekiwano tablicy"); return; }
        for (int i = 0; i < proposals.size(); i++) {
            JsonNode proposal = proposals.get(i);
            String p = "$.dictionaryProposals[" + i + "]";
            for (String name : List.of("name", "fieldTechName", "structure", "rationale")) text(proposal, name, p + "." + name, errors);
            if (DICTIONARIES.contains(proposal.path("name").asText())) errors.add(p + ".name: istniejącego słownika nie oznaczaj jako propozycji");
        }
    }

    private void collectTechNames(JsonNode tabs, Set<String> names) {
        for (JsonNode tab : tabs) collectQuestionTechNames(tab.path("questions"), names);
    }

    private void collectQuestionTechNames(JsonNode questions, Set<String> names) {
        if (!questions.isArray()) return;
        for (JsonNode question : questions) {
            JsonNode field = question.path("field");
            String name = field.path("techName").asText();
            if (!name.isBlank()) names.add(name);
            collectQuestionTechNames(field.path("repeatingSectionGrid"), names);
        }
    }

    private void validateSchemaTypes(JsonNode value, JsonNode schema, String path, List<String> errors) {
        JsonNode type = schema.get("type");
        if (type != null && type.isTextual() && !matchesType(value, type.asText())) {
            errors.add(path + ": typ JSON niezgodny ze schematem COI, oczekiwano " + type.asText());
            return;
        }
        if (value.isObject() && schema.path("properties").isObject()) {
            JsonNode required = schema.get("required");
            if (required != null && required.isArray()) {
                for (JsonNode property : required) {
                    if (property.isTextual() && !value.has(property.asText())) {
                        errors.add(path + "." + property.asText() + ": wymagane przez schemat COI");
                    }
                }
            }
            for (var property : schema.path("properties").properties()) {
                JsonNode child = value.get(property.getKey());
                if (child != null) validateSchemaTypes(child, property.getValue(), path + "." + property.getKey(), errors);
            }
            if (schema.path("additionalProperties").isBoolean()
                    && !schema.path("additionalProperties").asBoolean()) {
                for (var property : value.properties()) {
                    if (!schema.path("properties").has(property.getKey())) {
                        errors.add(path + "." + property.getKey() + ": pole niedozwolone przez schemat COI");
                    }
                }
            }
        } else if (value.isArray() && schema.has("items")) {
            for (int i = 0; i < value.size(); i++) validateSchemaTypes(value.get(i), schema.get("items"), path + "[" + i + "]", errors);
        }
    }

    private boolean matchesType(JsonNode value, String expected) {
        return switch (expected) {
            case "object" -> value.isObject(); case "array" -> value.isArray(); case "string" -> value.isTextual();
            case "number" -> value.isNumber(); case "integer" -> value.isIntegralNumber(); case "boolean" -> value.isBoolean();
            case "null" -> value.isNull(); default -> true;
        };
    }

    private void uuid(JsonNode node, String property, String path, Set<String> ids, List<String> errors) {
        String value = node.path(property).asText();
        try { UUID.fromString(value); if (!ids.add(value)) errors.add(path + ": UUID musi być unikalny"); }
        catch (IllegalArgumentException e) { errors.add(path + ": wymagany jest poprawny UUID"); }
    }

    private void text(JsonNode node, String property, String path, List<String> errors) {
        JsonNode value = node.get(property);
        if (value == null || !value.isTextual() || value.asText().isBlank()) errors.add(path + ": wymagany jest niepusty tekst");
    }

    private void optionalText(JsonNode node, String property, String path, List<String> errors) {
        if (node.has(property) && !node.get(property).isNull() && !node.get(property).isTextual()) errors.add(path + ": oczekiwano tekstu");
    }

    private void requireArray(JsonNode node, String property, String path, List<String> errors) {
        if (!node.has(property) || !node.get(property).isArray()) errors.add(path + "." + property + ": wymagane jest pole typu tablica");
    }

    private void nonNegativeInt(JsonNode node, String property, String path, List<String> errors) {
        JsonNode value = node.get(property);
        if (value == null || !value.isIntegralNumber() || value.asInt() < 0) errors.add(path + "." + property + ": wymagana jest liczba całkowita nieujemna");
    }

    private void positiveInt(JsonNode node, String property, String path, List<String> errors) {
        JsonNode value = node.get(property);
        if (value == null || !value.isIntegralNumber() || value.asInt() < 1) errors.add(path + "." + property + ": wymagana jest liczba całkowita większa od zera");
    }
}
