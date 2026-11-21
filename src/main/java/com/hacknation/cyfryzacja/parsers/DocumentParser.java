package com.hacknation.cyfryzacja.parsers;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hacknation.cyfryzacja.parsers.DocumentParseException.UnreadableDocumentException;

@Service
public class DocumentParser implements Parser {

    private final Tika tika = new Tika();

    @Override
    public String parse(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return tika.parseToString(input);
        } catch (IOException | TikaException e) {
            throw new UnreadableDocumentException("Nie udało się odczytać dokumentu", e);
        }
    }
}
