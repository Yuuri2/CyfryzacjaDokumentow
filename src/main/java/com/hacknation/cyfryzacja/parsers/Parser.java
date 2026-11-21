package com.hacknation.cyfryzacja.parsers;

import org.springframework.web.multipart.MultipartFile;

public interface Parser {
    String parse(MultipartFile file);
}