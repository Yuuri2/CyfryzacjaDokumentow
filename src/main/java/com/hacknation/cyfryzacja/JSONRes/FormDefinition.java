package com.hacknation.cyfryzacja.JSONRes;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FormDefinition(
        String title,
        List<FormTab> tabs,
        FormFlow flow
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FormTab(
            String id,
            String title,
            String type,
            List<FormQuestion> questions
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FormQuestion(
            int x,
            int y,
            int rows,
            int cols,
            Integer minItemRows,
            FormField field
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FormField(
            String id,
            String type,
            String techName,
            String title,
            String description,
            List<FieldOption> options,
            List<String> validation,
            String dictionaryName,
            Boolean multiLevelTree,
            Boolean searchEnabled,
            Boolean readOnly,
            List<FormQuestion> repeatingSectionGrid,
            Object defaultValue,
            String defaultValueType,
            ConsentSectionConfig consentSectionConfig,
            List<FieldState> states,
            Integer questionOrder,
            String tooltipMessage
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldOption(
            String id,
            String name,
            String techName
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConsentSectionConfig(
            List<ConsentItem> consents,
            Boolean required,
            List<String> files
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConsentItem(
            String description,
            Boolean required
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldState(
            String type,
            String query
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FormFlow(
            List<FlowLink> links
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FlowLink(
            String id,
            String source,
            String target
    ) {}
}