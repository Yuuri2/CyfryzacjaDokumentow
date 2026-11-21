package com.hacknation.cyfryzacja.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hacknation.cyfryzacja.parsers.DocumentParser;
import com.hacknation.cyfryzacja.walidacja.FileValidationServiece;
import com.hacknation.cyfryzacja.walidacja.FileValidationServiece.ValidatedFile;

@Service
public class UploadService {
    private final FileValidationServiece fileValidationServiece;
    private final DocumentParser documentParser;
    private final EdenOcrClient edenOcrClient;


    public UploadService(FileValidationService fileValidationServiece, DocumentParser documentParser) {
        this.fileValidationServiece = fileValidationServiece;
        this.documentParser = documentParser;
    }

    public void upload(MultipartFile file) {
        ValidatedFile validatedFile = fileValidationServiece.validate(file);

        String mimeType = validatedFile.mimeType();

        if (mimeType.equals("image/png")
                || mimeType.equals("image/jpeg")) {

            String extractedText =
                    edenOcrClient.extractText(validatedFile);

            System.out.println("Tekst odczytany przez Eden AI:");
            System.out.println(extractedText);

            return;
        }

        switch (mimeType) {
            case "application/pdf",
                 "application/msword",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.oasis.opendocument.spreadsheet",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/x-tika-msoffice",
                 "application/x-tika-ooxml":
                documentParser.parse(file);
                break;
            default:
                throw new IllegalArgumentException("Nieobsługiwany typ MIME: " + mimeType);
        }
    }
}