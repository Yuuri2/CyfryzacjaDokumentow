package com.hacknation.cyfryzacja.services;

import java.util.logging.Logger;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hacknation.cyfryzacja.parsers.DocumentParser;
import com.hacknation.cyfryzacja.walidacja.FileValidationService;
import com.hacknation.cyfryzacja.walidacja.FileValidationService.ValidatedFile;

@Service
public class UploadService {
    private final FileValidationService fileValidationServiece;
    private final DocumentParser documentParser;

    private static final Logger logger = Logger.getLogger(UploadService.class.getName());

   public UploadService(
        FileValidationService fileValidationServiece,
        DocumentParser documentParser) {
        this.fileValidationServiece = fileValidationServiece;
        this.documentParser = documentParser;
    }

    public void upload(MultipartFile file) {
        ValidatedFile validatedFile = fileValidationServiece.validate(file);

        String extractedText = documentParser.parse(validatedFile.content(), validatedFile.filename());
        if (extractedText.isBlank()) throw new IllegalArgumentException("Nie udało się odczytać tekstu z dokumentu");
        logger.info("Upload zweryfikowany i sparsowany; treść dokumentu nie jest zapisywana w logach.");
    }
}
