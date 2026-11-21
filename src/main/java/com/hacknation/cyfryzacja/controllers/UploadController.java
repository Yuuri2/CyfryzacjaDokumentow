package com.hacknation.cyfryzacja.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.hacknation.cyfryzacja.services.UploadService;

import org.springframework.http.MediaType;
import java.util.logging.Logger;

@Controller 
public class UploadController {
    
    private static final Logger logger = Logger.getLogger(UploadController.class.getName());
    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PutMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadMultipart(@RequestParam("file") MultipartFile file) {
        logger.info("Received multipart file: " + file.getOriginalFilename());
        uploadService.upload(file);
        return ResponseEntity.ok().build();
    }
}
