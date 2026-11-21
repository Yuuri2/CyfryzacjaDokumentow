package com.hacknation.cyfryzacja.parsers;

import java.io.IOException;

import org.apache.tika.exception.TikaException;
import org.springframework.web.multipart.MultipartFile;

public interface Parser {
    String parse(MultipartFile file) throws IOException, TikaException;
}