package com.hacknation.cyfryzacja.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hacknation.cyfryzacja.JSONRes.FormDefinition;
import com.hacknation.cyfryzacja.JSONRes.JSONRuleValidator;
import com.hacknation.cyfryzacja.ai.FormGenerationClient;
import com.hacknation.cyfryzacja.parsers.DocumentParser;
import com.hacknation.cyfryzacja.walidacja.FileValidationService.ValidatedFile;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class FTJGenerator {
    private static final Logger log = LoggerFactory.getLogger(FTJGenerator.class);
    private final FormGenerationClient formGenerationClient;
    private final DocumentParser documentParser;
    private final ObjectMapper objectMapper;
    private final JSONRuleValidator jsonRuleValidator;
    private final DictionaryCatalog dictionaryCatalog;
    private final InitialDataCatalog initialDataCatalog;

    public FTJGenerator(FormGenerationClient formGenerationClient, DocumentParser documentParser,
                        ObjectMapper objectMapper, JSONRuleValidator jsonRuleValidator,
                        DictionaryCatalog dictionaryCatalog, InitialDataCatalog initialDataCatalog) {
        this.formGenerationClient = formGenerationClient;
        this.documentParser = documentParser;
        this.objectMapper = objectMapper;
        this.jsonRuleValidator = jsonRuleValidator;
        this.dictionaryCatalog = dictionaryCatalog;
        this.initialDataCatalog = initialDataCatalog;
    }

    public FormDefinition generate(ValidatedFile validatedFile) {
        // Parse every supported format through Tika. Its OCR parser handles images and
        // PDFParserConfig enables OCR in addition to text extraction for scanned PDFs.
        long parseStarted = System.nanoTime();
        String extractedText = documentParser.parse(validatedFile.content(), validatedFile.filename());
        log.info("Document text extraction completed: chars={}, elapsedMs={}", extractedText.length(),
                (System.nanoTime() - parseStarted) / 1_000_000);
        if (extractedText.isBlank()) {
            throw new GeneratedFormException("Nie udało się odczytać treści formularza.");
        }

        long generationStarted = System.nanoTime();
        log.info("Starting form generation: inputChars={}", extractedText.length());
        String jsonText = formGenerationClient.generateFormDefinition(validatedFile.filename(), extractedText);
        log.info("Form generation completed: outputChars={}, elapsedMs={}", jsonText.length(),
                (System.nanoTime() - generationStarted) / 1_000_000);
        JsonNode root;
        try {
            root = objectMapper.readTree(jsonText);
        } catch (JacksonException e) {
            throw new GeneratedFormException("Model zwrócił niepoprawny JSON.", e);
        }

        dictionaryCatalog.reconcile(root);

        List<String> violations = new java.util.ArrayList<>(jsonRuleValidator.validate(root));
        violations.addAll(initialDataCatalog.validate(root));
        if (!violations.isEmpty()) {
            log.warn("Generated form failed COI validation (violations={}); requesting one source-based correction",
                    violations.size());
            List<String> feedback = new java.util.ArrayList<>(violations.subList(0, Math.min(30, violations.size())));
            if (violations.size() > feedback.size()) {
                feedback.add("... plus " + (violations.size() - feedback.size()) + " additional violations");
            }
            long correctionStarted = System.nanoTime();
            String correctedJson = formGenerationClient.regenerateFormDefinition(
                    validatedFile.filename(), extractedText, feedback);
            log.info("Correction generation completed: outputChars={}, elapsedMs={}", correctedJson.length(),
                    (System.nanoTime() - correctionStarted) / 1_000_000);
            try {
                root = objectMapper.readTree(correctedJson);
            } catch (JacksonException e) {
                throw new GeneratedFormException("Model zwrócił niepoprawny JSON także po próbie korekty.", e);
            }
            dictionaryCatalog.reconcile(root);
            violations = new java.util.ArrayList<>(jsonRuleValidator.validate(root));
            violations.addAll(initialDataCatalog.validate(root));
        }
        if (!violations.isEmpty()) {
            throw new GeneratedFormException("Wygenerowany formularz nie spełnia reguł COI.", violations);
        }
        try {
            return objectMapper.treeToValue(root, FormDefinition.class);
        } catch (JacksonException e) {
            throw new GeneratedFormException("Nie można zmapować poprawnego JSON-a na model formularza.", e);
        }
    }

    public static final class GeneratedFormException extends RuntimeException {
        private final List<String> violations;

        public GeneratedFormException(String message) {
            this(message, List.of(), null);
        }

        public GeneratedFormException(String message, Throwable cause) {
            this(message, List.of(), cause);
        }

        public GeneratedFormException(String message, List<String> violations) {
            this(message, violations, null);
        }

        private GeneratedFormException(String message, List<String> violations, Throwable cause) {
            super(message, cause);
            this.violations = List.copyOf(violations);
        }

        public List<String> getViolations() {
            return violations;
        }
    }
}
