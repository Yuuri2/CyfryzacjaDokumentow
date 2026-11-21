package com.hacknation.cyfryzacja.ai.FormSpec;

import java.util.List;

public record FormPageSpec(
        String id,
        String title,
        String type,
        List<QuestionSpec> questions
) {
}