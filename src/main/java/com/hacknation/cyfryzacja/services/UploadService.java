package com.hacknation.cyfryzacja.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hacknation.walidacja.FileValidationServiece;

@Service 
public class UploadService {
    private FileValidationServiece fileValidationServiece;

    public UploadService(FileValidationServiece fileValidationServiece) {
        this.fileValidationServiece = fileValidationServiece;
    }

    public void upload(MultipartFile file) {
        fileValidationServiece.validate(file);
    }
}
