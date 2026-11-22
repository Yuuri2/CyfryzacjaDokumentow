package com.hacknation.cyfryzacja.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Registry initialized from the dictionary names present in the competition reference forms. */
@Component
public class DictionaryCatalog {
    private final JsonNode entries;

    public DictionaryCatalog(ObjectMapper mapper) {
        try (var input = DictionaryCatalog.class.getResourceAsStream("/dictionary-catalog.json")) {
            if (input == null) throw new IllegalStateException("Brak dictionary-catalog.json");
            entries = mapper.readTree(input);
        } catch (java.io.IOException | JacksonException e) {
            throw new IllegalStateException("Nie można wczytać katalogu słowników", e);
        }
    }

    public List<String> knownNames() {
        List<String> names = new ArrayList<>();
        entries.forEach(item -> names.add(item.path("name").asText()));
        return List.copyOf(names);
    }

    public boolean contains(String name) {
        for (JsonNode entry : entries) if (entry.path("name").asText().equals(name)) return true;
        return false;
    }

    public String match(String techName, String title, String description) {
        String text = (techName + " " + title + " " + description).toLowerCase(Locale.ROOT);
        if (text.matches(".*\\b(kraj|kraje|państwo|państwa)\\b.*")) return "Kraje";
        if (text.matches(".*\\b(województwo|wojewodztwo)\\b.*")) return "Województwo";
        if (text.matches(".*\\bpowiat\\b.*")) return "Powiat";
        if (text.matches(".*\\bgmina\\b.*")) return "Gmina";
        if (text.matches(".*\\b(miejscowość|miejscowosc)\\b.*")) return "Miejscowość";
        if (text.matches(".*\\b(prefiks|numer kierunkowy)\\b.*")) return "Numery kierunkowe";
        return null;
    }

    public String promptText() {
        StringBuilder out = new StringBuilder("Predefiniowane słowniki COI (wstaw dokładną nazwę do dictionaryName):\n");
        for (JsonNode entry : entries) {
            out.append("- ").append(entry.path("name").asText()).append(" [pola: ");
            JsonNode fieldTypes = entry.path("fieldTypes");
            for (int i = 0; i < fieldTypes.size(); i++) {
                if (i > 0) out.append(", ");
                out.append(fieldTypes.get(i).asText());
            }
            out.append("]; wpisy są dostarczane przez platformę COI, nie przez ten pakiet.\n");
        }
        out.append("Nazwy TERYT_DOWODOWY i TERYT_4G_DOWODOWY występują w formularzach referencyjnych.\n");
        return out.toString();
    }

    public JsonNode publicCatalog() {
        return entries;
    }

    /** Keeps known dictionary names on COI fields and moves unknown names into review metadata. */
    public void reconcile(JsonNode root) {
        if (!(root instanceof ObjectNode form)) return;
        JsonNode proposalsNode = form.get("dictionaryProposals");
        ArrayNode proposals;
        if (proposalsNode instanceof ArrayNode existing) proposals = existing;
        else {
            proposals = form.arrayNode();
            form.set("dictionaryProposals", proposals);
        }
        for (JsonNode proposalNode : proposals) {
            if (proposalNode instanceof ObjectNode proposal) {
                proposal.put("origin", "generated-proposal");
            }
        }
        JsonNode tabs = form.get("tabs");
        if (tabs == null || !tabs.isArray()) return;
        for (JsonNode tab : tabs) {
            walkQuestions(tab.path("questions"), proposals);
        }
    }

    private void walkQuestions(JsonNode questions, ArrayNode proposals) {
        if (questions == null || !questions.isArray()) return;
        for (JsonNode question : questions) {
            JsonNode fieldNode = question.get("field");
            if (!(fieldNode instanceof ObjectNode field)) continue;
            applyKnownValidators(field);
            String dictionary = field.path("dictionaryName").asText("").trim();
            if (!dictionary.isEmpty() && !contains(dictionary)) {
                addProposal(proposals, dictionary, field.path("techName").asText(),
                        "Wykryto nazwę słownika w wyniku modelu, ale nie ma jej w katalogu materiałów konkursowych.");
                field.remove("dictionaryName");
            } else if (dictionary.isEmpty()
                    && List.of("SELECT", "RADIO", "CHECKBOX").contains(field.path("type").asText())) {
                String matched = match(field.path("techName").asText(), field.path("title").asText(), field.path("description").asText());
                if (matched != null) field.put("dictionaryName", matched);
            }
            walkQuestions(field.path("repeatingSectionGrid"), proposals);
        }
    }

    private void applyKnownValidators(ObjectNode field) {
        String cue = (field.path("techName").asText() + " " + field.path("title").asText() + " "
                + field.path("description").asText()).toLowerCase(Locale.ROOT);
        String[] rules = {"PESEL", "NIP", "REGON", "EMAIL", "ZIP_CODE", "EPUAP", "ADE", "SERIAL_NUMBER_ID_CARD"};
        String[][] cues = {
            {"pesel"}, {"nip"}, {"regon"}, {"e-mail", "email", "adres poczty"},
            {"kod pocztowy"}, {"e-puap", "epuap"}, {"ade"}, {"seria i numer dowodu", "numer dowodu osobistego"}
        };
        ArrayNode validation;
        JsonNode existing = field.get("validation");
        if (existing instanceof ArrayNode array) validation = array;
        else return;
        for (int i = 0; i < rules.length; i++) {
            boolean matched = false;
            for (String cuePart : cues[i]) if (cue.contains(cuePart)) matched = true;
            if (!matched) continue;
            boolean alreadyPresent = false;
            for (JsonNode rule : validation) if (rules[i].equals(rule.asText())) alreadyPresent = true;
            if (!alreadyPresent) validation.add(rules[i]);
            if ("NUMBER".equals(field.path("type").asText())) {
                field.put("type", "TEXT_FIELD");
            }
        }
    }

    private void addProposal(ArrayNode proposals, String name, String techName, String reason) {
        for (JsonNode proposal : proposals) {
            if (name.equalsIgnoreCase(proposal.path("name").asText())
                    && techName.equals(proposal.path("fieldTechName").asText())) return;
        }
        ObjectNode proposal = proposals.addObject();
        proposal.put("name", name);
        proposal.put("fieldTechName", techName);
        proposal.put("structure", "Wymaga uzupełnienia: typ danych, klucz, etykieta i źródło wartości.");
        proposal.put("rationale", reason);
        proposal.put("origin", "generated-proposal");
    }
}
