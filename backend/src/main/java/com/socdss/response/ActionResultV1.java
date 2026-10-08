package com.socdss.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

public record ActionResultV1(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("action_id") String actionId,
        @JsonProperty("incident_id") Long incidentId,
        @JsonProperty("action_type") String actionType,
        String target,
        String status,
        String message,
        @JsonProperty("completed_at") Instant completedAt,
        Map<String, Object> details
) {}
