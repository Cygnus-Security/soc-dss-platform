package com.socdss.alert;

import java.time.Instant;

/**
 * Lightweight alert projection used while grouping large imports. It deliberately
 * excludes rawJson and metadataJson so correlation does not read payload data that
 * it never uses.
 */
public interface CorrelationAlertView {
    Long getId();
    Instant getEventTimestamp();
    Instant getCreatedAt();
    Integer getWazuhRuleLevel();
    String getAgentName();
    String getSourceIp();
    String getIncidentType();
    String getDescription();
}
