package com.hacknation.cyfryzacja.formspec;

import java.util.List;

public record FieldSpec(
        String id,
        String type,
        String techName,
        String title,
        String description,

        List<OptionSpec> options,
        List<ValidationSpec> validation,

        String dictionaryName,

        boolean readOnly,
        boolean required,

        String defaultValue,
        String defaultValueType,

        List<FieldStateSpec> states,

        Double questionOrder,
        String tooltipMessage,

        // Informacje wykorzystywane tylko w FormSpec
        String semanticType,
        String initialDataSource,

        // Pola zagnieżdżone w sekcji
        List<QuestionSpec> children
) {
}