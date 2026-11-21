package com.hacknation.cyfryzacja.parsers;

import org.springframework.web.multipart.MultipartFile;

public interface Parser {
    public String parse(MultipartFile file);
}