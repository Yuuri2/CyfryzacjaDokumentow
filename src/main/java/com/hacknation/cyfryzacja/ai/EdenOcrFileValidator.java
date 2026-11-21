
package com.hacknation.cyfryzacja.ai;

import com.hacknation.cyfryzacja.walidacja.FileValidationServiece;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class EdenOcrFileValidator {

    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/png", "image/jpeg");

    public boolean supports(
            FileValidationServiece.ValidatedFile file
    ) {
        return file != null
                && ALLOWED_MIME_TYPES.contains(file.mimeType());
    }
}
