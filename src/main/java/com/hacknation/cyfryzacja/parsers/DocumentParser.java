package com.hacknation.cyfryzacja.parsers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import com.hacknation.cyfryzacja.parsers.DocumentParseException.UnreadableDocumentException;

@Service
public class DocumentParser implements Parser {
    private static final String OCR_LANGUAGE = "pol+eng";
    private final AutoDetectParser parser = new AutoDetectParser();

    @Override
    public String parse(MultipartFile file) {
        try {
            return parse(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new UnreadableDocumentException("Nie udało się odczytać dokumentu", e);
        }
    }

    /** Parses the already-validated in-memory upload. OCR is enabled for images and scanned PDFs. */
    public String parse(byte[] content, String filename) {
        try (InputStream input = new ByteArrayInputStream(content)) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set("resourceName", filename == null ? "document" : filename);

            TesseractOCRConfig tesseract = new TesseractOCRConfig();
            tesseract.setLanguage(OCR_LANGUAGE);
            PDFParserConfig pdf = new PDFParserConfig();
            // OCR only where the PDF has no extractable text, avoiding expensive
            // page rendering for ordinary digitally-generated PDFs.
            pdf.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.AUTO);

            ParseContext context = new ParseContext();
            context.set(TesseractOCRConfig.class, tesseract);
            context.set(PDFParserConfig.class, pdf);
            context.set(org.apache.tika.parser.Parser.class, parser);
            parser.parse(input, handler, metadata, context);

            String text = handler.toString().trim();
            if (text.isBlank()) {
                throw new UnreadableDocumentException("Dokument nie zawiera tekstu możliwego do odczytania", null);
            }
            return text;
        } catch (IOException | TikaException | SAXException e) {
            throw new UnreadableDocumentException("Nie udało się odczytać dokumentu lub wykonać OCR", e);
        }
    }
}
