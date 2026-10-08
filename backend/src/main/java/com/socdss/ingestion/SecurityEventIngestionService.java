package com.socdss.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socdss.alert.SecurityAlert;
import com.socdss.alert.SecurityAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SecurityEventIngestionService {
    private static final String ACCEPTED = "ACCEPTED";

    private final SecurityAlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    public SecurityEventIngestionService(SecurityAlertRepository alertRepository, ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SecurityEventBatchResult ingest(List<SecurityEventV1> events) {
        int imported = 0;
        int duplicates = 0;

        for (SecurityEventV1 event : events) {
            if (alertRepository.existsByTenantIdAndEventId(event.tenantId(), event.eventId())) {
                duplicates++;
                continue;
            }

            alertRepository.save(toAlert(event));
            imported++;
        }

        return new SecurityEventBatchResult(events.size(), imported, duplicates);
    }

    private SecurityAlert toAlert(SecurityEventV1 event) {
        SecurityAlert alert = new SecurityAlert();
        alert.setTenantId(event.tenantId());
        alert.setEventId(event.eventId());
        alert.setSchemaVersion(event.schemaVersion());
        alert.setExternalId(event.externalId());
        alert.setEventTimestamp(event.observedAt());
        alert.setIngestedAt(event.ingestedAt());
        alert.setSource(event.sourceProduct());
        alert.setWazuhRuleId(metadataText(event, "source_rule_id"));
        alert.setWazuhRuleLevel(toWazuhScale(event.severity()));
        alert.setNormalizedSeverity(event.severity());
        alert.setConfidence(event.confidence());
        alert.setAgentName(event.assetId() == null ? "unknown" : event.assetId());
        alert.setSourceIp(event.sourceIp());
        alert.setDestinationIp(event.destinationIp());
        alert.setDescription(String.join(", ", event.evidence()));
        alert.setMitreTechnique(first(event.mitreTechniques()));
        alert.setIncidentType(event.eventType());
        alert.setRawReference(event.rawReference());
        alert.setNormalizerVersion(event.normalizerVersion());
        alert.setMetadataJson(writeMetadata(event));
        alert.setIngestionStatus(ACCEPTED);
        return alert;
    }

    private int toWazuhScale(double severity) {
        return (int) Math.round(Math.min(100.0, Math.max(0.0, severity)) * 16.0 / 100.0);
    }

    private String metadataText(SecurityEventV1 event, String key) {
        if (event.metadata() == null || event.metadata().get(key) == null) {
            return null;
        }
        return String.valueOf(event.metadata().get(key));
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String writeMetadata(SecurityEventV1 event) {
        try {
            return objectMapper.writeValueAsString(event.metadata() == null ? java.util.Map.of() : event.metadata());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize event metadata", e);
        }
    }
}
