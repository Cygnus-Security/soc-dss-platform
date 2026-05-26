package com.socdss.risk;

public record RiskAssessmentResult(
        double score,
        String level,
        String explanation
) {}
