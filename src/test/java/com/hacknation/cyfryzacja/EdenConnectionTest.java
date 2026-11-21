
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

        Path path = Path.of(
                "C:\\Users\\guzma\\OneDrive\\Pulpit\\hacknation2026_Cyfryzacja\\CyfryzacjaDokumentow\\Materiały\\Materiały_dc\\1. Wniosek o ustalenie numeru porządkowego nieruchomości - materiał referencyjny dla Uczestników do zadania\\Wniosek o ustalenie numeru porządkowego nieruchomości.pdf"
        );

        byte[] content = Files.readAllBytes(path);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                path.getFileName().toString(),
                "application/pdf",
                content
        );


        JsonNode response = edenOcrClient.extract(file);

        assertNotNull(response);

        System.out.println("ODPOWIEDŹ EDEN AI:");
        System.out.println(response.toPrettyString());
    }
}
