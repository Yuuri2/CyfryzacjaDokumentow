package com.hacknation.cyfryzacja.walidacja;

import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.io.IOException;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service 
public class FileValidationServiece {
    
    private static final int maxNameLength = 250;
    private static final Set<Character> forbiddenChars = Set.of(
            ';', ':', '$', '<', '>', '/', '\\', '|', '\'', '*', '%', '?'
    );

    private static final Map<String, Set<String>> legalExtensions = Map.ofEntries(
        Map.entry("pdf", Set.of("application/pdf")),
        Map.entry("doc", Set.of("application/msword", "application/x-tika-msoffice")),
        Map.entry("docx", Set.of(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/x-tika-ooxml"
        )),
        Map.entry("ods", Set.of("application/vnd.oasis.opendocument.spreadsheet")),
        Map.entry("xlsx", Set.of(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/x-tika-ooxml"
        )),
        Map.entry("jpg", Set.of("image/jpeg")),
        Map.entry("jpeg", Set.of("image/jpeg")),
        Map.entry("png", Set.of("image/png"))
    );

    private final Tika tika = new Tika();
    private final long maxFileSize;

    public FileValidationServiece(@Value("${app.upload.max-size-bytes:10485760}") long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }
    //tutaj dodajemy zabezpieczenia przed błędnymi plikami.
    public ValidatedFile validate(MultipartFile file) {
        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException("Plik jest pusty");
        }
        String originalFilename = file.getOriginalFilename();
        if(originalFilename == null || originalFilename.isBlank()){
            throw new IllegalArgumentException("Plik nie ma nazwy");
        }
        if(originalFilename.length() > maxNameLength){
            throw new IllegalArgumentException("Nazwa pliku jest za długa");
        }
        for(char c : originalFilename.toCharArray()){
            if(forbiddenChars.contains(c)){
                throw new IllegalArgumentException("Nazwa pliku zawiera niedozwolone znaki");
            }
        }
        if(file.getSize() > maxFileSize){
            throw new IllegalArgumentException("Plik jest za duży");
        }
        String extension = extractExtension(originalFilename);
        Set<String> allowedMimeTypes = legalExtensions.get(extension);
        if(allowedMimeTypes == null){
            throw new IllegalArgumentException("Nieobsługiwany typ pliku: " + extension);
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Nie można odczytać zawartości pliku", e);
        }
        String detectedMime = tika.detect(content, originalFilename);
        if(detectedMime == null || !legalExtensions.get(extension).contains(detectedMime.toLowerCase(Locale.ROOT))){
            throw new IllegalArgumentException("Nieobsługiwany typ MIME: " + detectedMime);
        }
        return new ValidatedFile(originalFilename, extension, detectedMime.toLowerCase(Locale.ROOT), content);
    }
    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("Plik musi posiadać rozszerzenie.");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
    public record ValidatedFile(String filename, String extension, String mimeType, byte[] content) {}
}
