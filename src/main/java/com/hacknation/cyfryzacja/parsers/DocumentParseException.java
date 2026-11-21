package com.hacknation.cyfryzacja.parsers;

public class DocumentParseException extends RuntimeException {
    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class UnsupportedDocumentException extends DocumentParseException {
        public UnsupportedDocumentException(String message) {
            super(message);
        }
    }

    public static class UnreadableDocumentException extends DocumentParseException {
        public UnreadableDocumentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
