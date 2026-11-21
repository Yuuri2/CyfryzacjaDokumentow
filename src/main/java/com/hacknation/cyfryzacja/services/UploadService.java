package com.hacknation.cyfryzacja.services;

import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.logging.Logger;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hacknation.cyfryzacja.parsers.PdfParser;
import com.hacknation.cyfryzacja.walidacja.FileValidationServiece;

@Service
public class UploadService {
    private static final Logger logger = Logger.getLogger(UploadService.class.getName());

    private final FileValidationServiece fileValidationServiece;
    private final PdfParser pdfParser;

    public UploadService(FileValidationServiece fileValidationServiece, PdfParser pdfParser) {
        this.fileValidationServiece = fileValidationServiece;
        this.pdfParser = pdfParser;
    }

    public void upload(MultipartFile file) {
        fileValidationServiece.validate(file);
        try {
            var pdfContents = pdfParser.parse(file);
            logger.info(pdfContents);
        } catch( Exception exception) {
            logger.warning(exception.toString());
        }

    }
}