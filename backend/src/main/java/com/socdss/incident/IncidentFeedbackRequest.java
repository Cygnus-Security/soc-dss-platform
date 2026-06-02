package com.socdss.incident;

public record IncidentFeedbackRequest(
        String analystVerdict,
        String analystNotes,
        String decisionStatus
) {}
