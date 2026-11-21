package com.hacknation.cyfryzacja.formspec;

import java.util.List;

public record FormPageSpec(
        String id,
        String title,
        String type,
        List<QuestionSpec> questions
) {
}