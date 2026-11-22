package com.hacknation.cyfryzacja.JSONRes;

import java.util.List;

public record JSONResponseValidator(
    boolean isValid,
    List<RuleViolation> violations
) {
    public static JSONResponseValidator ok(){
        return new JSONResponseValidator(true, List.of());
    }

    public static JSONResponseValidator invalid(List<RuleViolation> violations){
        return new JSONResponseValidator(violations.isEmpty(), violations);
    }

    public record RuleViolation(
        String ruleCode,
        String path,
        String message
    ) {}
}
