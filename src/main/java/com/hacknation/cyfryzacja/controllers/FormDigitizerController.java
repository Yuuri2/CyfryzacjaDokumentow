package com.hacknation.cyfryzacja.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.hacknation.cyfryzacja.parsers.DocumentParseException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import com.hacknation.cyfryzacja.JSONRes.FormDefinition;
import com.hacknation.cyfryzacja.services.FTJGenerator;
import com.hacknation.cyfryzacja.walidacja.FileValidationService;
import com.hacknation.cyfryzacja.walidacja.FileValidationService.ValidatedFile;

@RestController 
@RequestMapping ("/api/forms")
public class FormDigitizerController {
    private final FileValidationService fileValidationService;    
    private final FTJGenerator ftjGenerator;
    private final com.hacknation.cyfryzacja.services.DictionaryCatalog dictionaryCatalog;

    public FormDigitizerController(FileValidationService fileValidationService, FTJGenerator ftjGenerator,
                                   com.hacknation.cyfryzacja.services.DictionaryCatalog dictionaryCatalog) {
        this.fileValidationService = fileValidationService;
        this.ftjGenerator = ftjGenerator;
        this.dictionaryCatalog = dictionaryCatalog;
    }

    @PostMapping(value = "/digitize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FormDefinition> digitize(@RequestParam("file") MultipartFile file) {
        ValidatedFile validatedFile = fileValidationService.validate(file);
        FormDefinition formDefinition = ftjGenerator.generate(validatedFile);
        return ResponseEntity.ok(formDefinition);
    }

    @PostMapping(value = "/digitize/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<BatchResult>> digitizeBatch(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("Przekaż co najmniej jeden plik w polu files");
        if (files.size() > 10) throw new IllegalArgumentException("Jedno żądanie zbiorcze może zawierać najwyżej 10 plików");
        List<BatchResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            String filename = file == null ? "" : String.valueOf(file.getOriginalFilename());
            try {
                ValidatedFile validatedFile = fileValidationService.validate(file);
                results.add(new BatchResult(filename, ftjGenerator.generate(validatedFile), null, List.of()));
            } catch (FTJGenerator.GeneratedFormException e) {
                results.add(new BatchResult(filename, null, e.getMessage(), e.getViolations()));
            } catch (RuntimeException e) {
                results.add(new BatchResult(filename, null,
                        e.getMessage() == null ? "Nie udało się przetworzyć pliku" : e.getMessage(), List.of()));
            }
        }
        return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(List.copyOf(results));
    }

    public record BatchResult(String filename, FormDefinition form, String error, List<String> violations) {}

    @org.springframework.web.bind.annotation.GetMapping("/dictionaries")
    public tools.jackson.databind.JsonNode dictionaries() {
        return dictionaryCatalog.publicCatalog();
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> edenHttpError(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 429) {
            return ResponseEntity.status(429).body("Eden AI ogranicza liczbę żądań. Spróbuj ponownie za chwilę.");
        }
        if (status == 502 || status == 503 || status == 504) {
            return ResponseEntity.status(503).body("Usługa Eden AI jest chwilowo niedostępna. Spróbuj ponownie.");
        }
        return ResponseEntity.status(502).body("Eden AI zwróciło błąd HTTP " + status + ".");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> edenStateError(IllegalStateException exception) {
        return ResponseEntity.status(503).body(exception.getMessage());
    }

    @ExceptionHandler(FTJGenerator.GeneratedFormException.class)
    public ResponseEntity<Map<String, Object>> invalidGeneratedForm(FTJGenerator.GeneratedFormException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", exception.getMessage(),
                "violations", exception.getViolations()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidUpload(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(DocumentParseException.class)
    public ResponseEntity<Map<String, String>> unreadableDocument(DocumentParseException exception) {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", exception.getMessage()));
    }
}
