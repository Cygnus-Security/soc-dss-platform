package com.socdss.ingestion;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SecurityEventV1(
        @JsonProperty("schema_version")
        @NotBlank
        @Pattern(regexp = "1\\.0")
        String schemaVersion,

        @JsonProperty("event_id")
        @NotBlank
        @Size(min = 16, max = 128)
        String eventId,

        @JsonProperty("tenant_id")
        @NotBlank
        @Size(max = 64)
        String tenantId,

        @JsonProperty("source_type")
        @NotNull
        SourceType sourceType,

        @JsonProperty("source_product")
        @NotBlank
        @Size(max = 64)
        String sourceProduct,

        @JsonProperty("external_id")
        @NotBlank
        @Size(max = 255)
        String externalId,

        @JsonProperty("observed_at")
        @NotNull
        Instant observedAt,

        @JsonProperty("ingested_at")
        @NotNull
        Instant ingestedAt,

        @JsonProperty("event_type")
        @NotBlank
        @Size(max = 128)
        String eventType,

        @JsonProperty("source_severity")
        Double sourceSeverity,

        @DecimalMin("0.0")
        @DecimalMax("100.0")
        double severity,

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        double confidence,

        @JsonProperty("asset_id")
        @Size(max = 128)
        String assetId,

        @JsonProperty("source_ip")
        String sourceIp,

        @JsonProperty("destination_ip")
        String destinationIp,

        @JsonProperty("user_id")
        @Size(max = 255)
        String userId,

        @JsonProperty("session_id")
        @Size(max = 255)
        String sessionId,

        @JsonProperty("mitre_techniques")
        @NotNull
        List<@Pattern(regexp = "T[0-9]{4}(\\.[0-9]{3})?") String> mitreTechniques,

        @JsonProperty("cve_ids")
        @NotNull
        List<@Pattern(regexp = "CVE-[0-9]{4}-[0-9]{4,}") String> cveIds,

        @NotNull
        List<@NotBlank @Size(max = 255) String> evidence,

        @JsonProperty("raw_reference")
        @NotBlank
        @Size(max = 1024)
        String rawReference,

        @JsonProperty("normalizer_version")
        @NotBlank
        @Size(max = 64)
        String normalizerVersion,

        Map<String, Object> metadata
) {
    public enum SourceType {
        wazuh,
        endpoint,
        ids,
        firewall,
        web,
        application,
        auth,
        audit,
        container,
        asset,
        vulnerability
    }
}
