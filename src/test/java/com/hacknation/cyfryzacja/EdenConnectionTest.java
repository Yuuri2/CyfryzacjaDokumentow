
package com.hacknation.cyfryzacja;

import tools.jackson.databind.JsonNode;
import com.hacknation.cyfryzacja.ai.EdenOcrClient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class EdenAiConnectionTest {

    @Autowired
    private EdenOcrClient edenOcrClient;

    @Test
    void shouldConnectToEdenAiAndPerformOcr() throws Exception {

        System.out.println("================================");
        System.out.println("START TESTU EDEN AI OCR");
        System.out.println("================================");

        Path path = Path.of(
                "C:\\Users\\guzma\\OneDrive\\Pulpit\\hacknation2026_Cyfryzacja\\CyfryzacjaDokumentow\\Materiały\\Materiały_dc\\1. Wniosek o ustalenie numeru porządkowego nieruchomości - materiał referencyjny dla Uczestników do zadania\\Wniosek o ustalenie numeru porządkowego nieruchomości.pdf"
        );

        System.out.println("1. Sprawdzanie pliku...");

        if (!Files.exists(path)) {
            throw new IllegalStateException(
                    "Nie znaleziono pliku: " + path
            );
        }

        byte[] content = Files.readAllBytes(path);

        System.out.println("2. Plik odczytany.");
        System.out.println("Rozmiar: " + content.length + " bajtów");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                path.getFileName().toString(),
                "application/pdf",
                content
        );

        System.out.println("3. Wysyłanie pliku do Eden AI i wykonywanie OCR...");

        JsonNode response = edenOcrClient.extract(file);

        System.out.println("4. Otrzymano odpowiedź z Eden AI.");

        assertNotNull(response, "Odpowiedź Eden AI nie może być null");

        System.out.println("================================");
        System.out.println("ODPOWIEDŹ EDEN AI:");
        System.out.println("================================");

        System.out.println(response.toPrettyString());

        System.out.println("================================");
        System.out.println("KONIEC TESTU");
        System.out.println("================================");
    }
}
