package com.socdss.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socdss.incident.Incident;
import com.socdss.incident.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResponseActionServiceTest {
    private final IncidentRepository incidentRepository = mock(IncidentRepository.class);
    private final ResponseActionRepository actionRepository = mock(ResponseActionRepository.class);
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ResponseActionService service = new ResponseActionService(
            incidentRepository,
            actionRepository,
            new ApprovalTokenService("test-approval-secret-with-at-least-32-characters"),
            kafkaTemplate,
            new ObjectMapper().findAndRegisterModules(),
            "soc.action-approved.v1",
            true,
            10
    );

    @Test
    void approvesPendingActionAndDispatchesSignedMessage() {
        Incident incident = new Incident();
        incident.setId(42L);
        ResponseAction action = pendingAction(incident);
        when(actionRepository.findById(action.getId())).thenReturn(Optional.of(action));
        when(kafkaTemplate.send(eq("soc.action-approved.v1"), eq(action.getId()), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ResponseActionDto result = service.approve(42L, action.getId(), new ActionDecisionRequest("analyst", null));

        assertThat(result.status()).isEqualTo("DISPATCHED");
        assertThat(result.approvalExpiresAt()).isAfter(Instant.now());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("soc.action-approved.v1"), eq(action.getId()), payload.capture());
        assertThat(payload.getValue())
                .contains("\"approval_token\"")
                .contains("\"action_type\":\"temporary_ip_block\"")
                .contains("\"incident_id\":42");
    }

    @Test
    void exportsFilteredActionsAsExcelSafeCsv() {
        Incident incident = new Incident();
        incident.setId(42L);
        ResponseAction action = pendingAction(incident);
        action.setTarget("=malicious-formula");
        action.setRationale("Contain \"quoted\" target");
        action.setResultMessage("line one\nline two");
        when(actionRepository.searchForExport("EXECUTED", "temporary_ip_block", "malicious"))
                .thenReturn(List.of(action));

        String csv = service.exportCsv(" executed ", " temporary_ip_block ", " malicious ");

        assertThat(csv).startsWith("\uFEFFaction_id,incident_id,action_type");
        assertThat(csv).contains("\"'=malicious-formula\"");
        assertThat(csv).contains("\"Contain \"\"quoted\"\" target\"");
        assertThat(csv).contains("\"line one\nline two\"");
    }

    private ResponseAction pendingAction(Incident incident) {
        ResponseAction action = new ResponseAction();
        action.setId("11111111-1111-1111-1111-111111111111");
        action.setIncident(incident);
        action.setActionType("temporary_ip_block");
        action.setTarget("192.0.2.10");
        action.setRationale("Contain suspicious source");
        action.setStatus("PENDING_APPROVAL");
        action.setRequestedAt(Instant.now());
        return action;
    }
}
