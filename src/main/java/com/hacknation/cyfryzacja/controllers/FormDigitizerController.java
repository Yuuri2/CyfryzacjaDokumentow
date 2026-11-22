package com.hacknation.cyfryzacja.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClientResponseException;

import com.hacknation.cyfryzacja.JSONRes.FormDefinition;
import com.hacknation.cyfryzacja.services.FTJGenerator;
import com.hacknation.cyfryzacja.walidacja.FileValidationService;
import com.hacknation.cyfryzacja.walidacja.FileValidationService.ValidatedFile;

@RestController 
@RequestMapping ("/api/forms")
public class FormDigitizerController {
    private final FileValidationService fileValidationService;    
    private final FTJGenerator ftjGenerator;

    public FormDigitizerController(FileValidationService fileValidationService, FTJGenerator ftjGenerator) {
        this.fileValidationService = fileValidationService;
        this.ftjGenerator = ftjGenerator;
    }

    @PostMapping(value = "/digitize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FormDefinition> digitize(@RequestParam("file") MultipartFile file) {
        ValidatedFile validatedFile = fileValidationService.validate(file);
        FormDefinition formDefinition = ftjGenerator.generate(validatedFile, file);
        return ResponseEntity.ok(formDefinition);
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
}
