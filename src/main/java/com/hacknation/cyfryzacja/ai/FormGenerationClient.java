package com.hacknation.cyfryzacja.ai;

public interface FormGenerationClient {
    String generateFormDefinition(String filename, String extractedText);
}
