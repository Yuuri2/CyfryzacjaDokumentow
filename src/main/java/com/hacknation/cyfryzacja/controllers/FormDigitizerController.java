package com.hacknation.cyfryzacja.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
}
