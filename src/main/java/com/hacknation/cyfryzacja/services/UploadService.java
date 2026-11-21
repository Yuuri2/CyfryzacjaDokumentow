package com.hacknation.cyfryzacja.services;

import java.util.logging.Logger;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hacknation.cyfryzacja.parsers.DocumentParser;
import com.hacknation.cyfryzacja.walidacja.FileValidationServiece;

@Service
public class UploadService {

    private static final Logger logger = Logger.getLogger(UploadService.class.getName());
    private final FileValidationServiece fileValidationServiece;
    private final DocumentParser documentParser;

    public UploadService(FileValidationServiece fileValidationServiece, DocumentParser documentParser) {
        this.fileValidationServiece = fileValidationServiece;
        this.documentParser = documentParser;
    }

    public void upload(MultipartFile file) {
        fileValidationServiece.validate(file);
        var parsedDocument = documentParser.parse(file);
        logger.info(parsedDocument);
    }
}