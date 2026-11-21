package com.hacknation.cyfryzacja.parsers;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import com.hacknation.cyfryzacja.parsers.DocumentParseException.UnreadableDocumentException;

@Service
public class PdfParser implements Parser {

    private final PDFParser pdfParser = new PDFParser();

    @Override
    public String parse(MultipartFile file) {
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");

        try (InputStream input = file.getInputStream()) {
            pdfParser.parse(input, handler, metadata, new ParseContext());
            return handler.toString();
        } catch (IOException | TikaException e) {
            throw new UnreadableDocumentException("Nie udało się odczytać pliku PDF", e);
        } catch (SAXException e) {
            throw new UnreadableDocumentException("Nie udało się przetworzyć tekstu z pliku PDF", e);
        }
    }
}
