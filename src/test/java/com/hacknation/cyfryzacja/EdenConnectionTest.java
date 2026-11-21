package com.hacknation.cyfryzacja;

import com.hacknation.cyfryzacja.ai.EdenOcrClient;
import com.hacknation.cyfryzacja.walidacja.FileValidationServiece;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class EdenAiConnectionTest {

    @Autowired
    private EdenOcrClient edenOcrClient;

    @Autowired
    private FileValidationServiece fileValidationServiece;

    @Test
    void shouldConnectToEdenAiAndPerformOcr() throws Exception {

        // Ścieżka do testowego obrazu
        Path path = Path.of(
                "Materiały",
                "Materiały_dc",
                "1. Wniosek o ustalenie numeru porządkowego nieruchomości - materiał referencyjny dla Uczestników do zadania",
                "Wniosek o ustalenie numeru porządkowego nieruchomości (1).png"
        );

        // Odczyt zawartości pliku
        byte[] content = Files.readAllBytes(path);

        // Utworzenie pliku testowego
        MockMultipartFile file = new MockMultipartFile(
                "file",
                path.getFileName().toString(),
                "image/png",
                content
        );

        // Walidacja pliku
        FileValidationServiece.ValidatedFile validatedFile =
                fileValidationServiece.validate(file);

        try {
            // Wysłanie obrazu do Eden AI i wykonanie OCR
            String extractedText = edenOcrClient.extractText(validatedFile);

            // Sprawdzenie, czy OCR zwrócił tekst
            assertFalse(extractedText.isBlank());

            System.out.println("TEKST ODCZYTANY PRZEZ EDEN AI:");
            System.out.println(extractedText);

        } catch (HttpClientErrorException e) {
    String errorBody = e.getResponseBodyAsString();

    throw new AssertionError(
            "Eden AI zwrócił błąd HTTP: " + e.getStatusCode()
                    + "\nTreść odpowiedzi: " + errorBody,
            e
    );
}

    }
}