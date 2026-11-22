package com.hacknation.cyfryzacja.ai;

import java.util.List;

public interface FormGenerationClient {
    String generateFormDefinition(String filename, String extractedText);

    default String regenerateFormDefinition(String filename, String extractedText, List<String> violations) {
        return generateFormDefinition(filename, extractedText);
    }
}
