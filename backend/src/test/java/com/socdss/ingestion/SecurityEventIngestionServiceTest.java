package com.socdss.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socdss.alert.SecurityAlert;
import com.socdss.alert.SecurityAlertRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityEventIngestionServiceTest {
    private final SecurityAlertRepository repository = mock(SecurityAlertRepository.class);
    private final SecurityEventIngestionService service = new SecurityEventIngestionService(repository, new ObjectMapper());

    @Test
    void importsNewEventAndMapsNormalizedFields() {
        SecurityEventV1 event = event("event-0000000001");
        when(repository.existsByTenantIdAndEventId("lab", event.eventId())).thenReturn(false);

        SecurityEventBatchResult result = service.ingest(List.of(event));

        ArgumentCaptor<SecurityAlert> captor = ArgumentCaptor.forClass(SecurityAlert.class);
        verify(repository).save(captor.capture());
        SecurityAlert saved = captor.getValue();

        assertThat(result).isEqualTo(new SecurityEventBatchResult(1, 1, 0));
        assertThat(saved.getTenantId()).isEqualTo("lab");
        assertThat(saved.getEventId()).isEqualTo(event.eventId());
        assertThat(saved.getNormalizedSeverity()).isEqualTo(75.0);
        assertThat(saved.getWazuhRuleLevel()).isEqualTo(12);
        assertThat(saved.getIncidentType()).isEqualTo("authentication_bypass_attempt");
        assertThat(saved.getIngestionStatus()).isEqualTo("ACCEPTED");
        assertThat(saved.getMetadataJson()).contains("source_rule_id");
    }

    @Test
    void countsDuplicateWithoutSavingItAgain() {
        SecurityEventV1 event = event("event-0000000002");
        when(repository.existsByTenantIdAndEventId("lab", event.eventId())).thenReturn(true);

        SecurityEventBatchResult result = service.ingest(List.of(event));

        assertThat(result).isEqualTo(new SecurityEventBatchResult(1, 0, 1));
        verify(repository, never()).save(any());
    }

    private SecurityEventV1 event(String eventId) {
        return new SecurityEventV1(
                "1.0",
                eventId,
                "lab",
                SecurityEventV1.SourceType.ids,
                "suricata",
                "source-1",
                Instant.parse("2026-10-08T03:30:00Z"),
                Instant.parse("2026-10-08T03:30:05Z"),
                "authentication_bypass_attempt",
                10.0,
                75.0,
                0.97,
                "asset-001",
                "192.0.2.10",
                "192.0.2.20",
                null,
                "session-001",
                List.of("T1190"),
                List.of(),
                List.of("sqli_pattern_detected"),
                "wazuh-index:document-1",
                "translator-1.0.0",
                Map.of("source_rule_id", "100001")
        );
    }
}
