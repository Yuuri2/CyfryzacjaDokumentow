
package com.hacknation.cyfryzacja.ai;

import com.hacknation.cyfryzacja.walidacja.FileValidationServiece;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EdenValidationTest {

    private final EdenOcrFileValidator validator =
            new EdenOcrFileValidator();

    private FileValidationServiece.ValidatedFile createFile(
            String mimeType
    ) {
        return new FileValidationServiece.ValidatedFile(
                "test",
                "test",
                mimeType,
                new byte[0]
        );
    }

    @Test
    void shouldAcceptPng() {
        assertTrue(validator.supports(createFile("image/png")));
    }

    @Test
    void shouldAcceptJpeg() {
        assertTrue(validator.supports(createFile("image/jpeg")));
    }

    @Test
    void shouldRejectPdf() {
        assertFalse(validator.supports(createFile("application/pdf")));
    }

    @Test
    void shouldRejectDocx() {
        assertFalse(validator.supports(
                createFile("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        ));
    }

    @Test
    void shouldRejectNull() {
        assertFalse(validator.supports(null));
    }
}
