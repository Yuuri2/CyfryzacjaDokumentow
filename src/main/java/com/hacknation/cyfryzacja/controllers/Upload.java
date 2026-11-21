package com.hacknation.cyfryzacja.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.MediaType;
import java.util.logging.Logger;

@Controller 
public class Upload {
    
    private static final Logger logger = Logger.getLogger(Upload.class.getName());

    @PutMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadMultipart(@RequestParam("file") MultipartFile file) {
        logger.info("Received multipart file: " + file.getOriginalFilename());
        return ResponseEntity.ok().build();
    }
}
