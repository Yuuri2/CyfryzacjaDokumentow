package com.hacknation.cyfryzacja.ai.FormSpec;

import java.util.List;

public record FormSpec(
        String title,
        List<FormPageSpec> pages,
        FormFlowSpec flow
) {
}