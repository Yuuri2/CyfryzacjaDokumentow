package com.hacknation.cyfryzacja.formspec;

import java.util.List;

public record FormSpec(
        String title,
        List<FormPageSpec> pages,
        FormFlowSpec flow
) {
}