package com.hacknation.cyfryzacja.JSONRes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.ConsentSectionConfig;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.FieldOption;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.FieldState;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.FlowLink;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.FormField;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.FormFlow;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.FormQuestion;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.FormTab;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JSONRuleValidator {
    private final static int maxTitleLength = 255;
    private final static Set<String> legalTabTypes = Set.of("MAIN", "FINISH");

    private final static Set<String> legalFieldTypes = Set.of(
            "TEXT_FIELD", 
            "NUMBER_FIELD", 
            "DATE_FIELD", 
            "CHECKBOX", 
            "RADIO_BUTTON",
            "DROPDOWN", 
            "MULTI_SELECT", 
            "FILE_UPLOAD", 
            "REPEATING_SECTION",
            "EXTERNAL_SYSTEM"
    );

    private final static Set<String> legalSectionTypes = Set.of(
        "INFO_SECTION",
        "TEXT_SECTION"
    );

    private final static Set<String> legalValidators = Set.of(
        "REQUIRED",
        "EMAIL",
        "PESEL",
        "NIP",
        "REGON",
        "KRS",
        "ZIP_CODE",
        "PHONE",
        "MIN_LENGTH",
        "MAX_LENGTH",
        "REGEX"
    );


}
