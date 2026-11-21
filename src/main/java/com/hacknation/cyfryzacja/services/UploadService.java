package com.hacknation.cyfryzacja.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hacknation.cyfryzacja.parsers.PdfParser;
import com.hacknation.cyfryzacja.walidacja.FileValidationServiece;

@Service
public class UploadService {
    private final FileValidationServiece fileValidationServiece;
    private final PdfParser pdfParser;

    public UploadService(FileValidationServiece fileValidationServiece, PdfParser pdfParser) {
        this.fileValidationServiece = fileValidationServiece;
        this.pdfParser = pdfParser;
    }

    public void upload(MultipartFile file) {
        fileValidationServiece.validate(file);
        pdfParser.parse(file);
    }
}