package com.socdss.response;

import com.socdss.incident.Incident;
import com.socdss.incident.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BulkResponseActionServiceTest {
    private final IncidentRepository incidentRepository = mock(IncidentRepository.class);
    private final ResponseActionRepository actionRepository = mock(ResponseActionRepository.class);
    private final ResponseActionService actionService = mock(ResponseActionService.class);
    private final BulkResponseActionService service = new BulkResponseActionService(
            incidentRepository,
            actionRepository,
            actionService
    );

    @Test
    void dispatchesUniqueIncidentsOneAtATimeInRequestOrder() {
        Incident first = incident(11L, "192.0.2.11");
        Incident second = incident(12L, "192.0.2.12");
        when(incidentRepository.findById(11L)).thenReturn(Optional.of(first));
        when(incidentRepository.findById(12L)).thenReturn(Optional.of(second));
        when(actionRepository.findFirstByIncidentIdAndActionTypeAndStatusInOrderByRequestedAtDesc(
                any(), eq("temporary_ip_block"), anyCollection()
        )).thenReturn(Optional.empty());

        when(actionService.create(eq(11L), any())).thenReturn(action("action-11", first, "PENDING_APPROVAL"));
        when(actionService.approve(eq(11L), eq("action-11"), any())).thenReturn(action("action-11", first, "DISPATCHED"));
        when(actionService.create(eq(12L), any())).thenReturn(action("action-12", second, "PENDING_APPROVAL"));
        when(actionService.approve(eq(12L), eq("action-12"), any())).thenReturn(action("action-12", second, "DISPATCHED"));

        BulkDispatchResultDto result = service.dispatch(
                new BulkDispatchRequest(
                        List.of(11L, 12L, 11L),
                        "temporary_ip_block",
                        "Contain selected attack sources"
                ),
                "analyst"
        );

        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.dispatched()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.items()).extracting(BulkDispatchItemDto::incidentId).containsExactly(11L, 12L);

        InOrder calls = inOrder(actionService);
        calls.verify(actionService).create(eq(11L), any());
        calls.verify(actionService).approve(eq(11L), eq("action-11"), any());
        calls.verify(actionService).create(eq(12L), any());
        calls.verify(actionService).approve(eq(12L), eq("action-12"), any());
    }

    @Test
    void reusesPendingActionAndSkipsAlreadyDispatchedAction() {
        Incident first = incident(21L, "198.51.100.21");
        Incident second = incident(22L, "198.51.100.22");
        when(incidentRepository.findById(21L)).thenReturn(Optional.of(first));
        when(incidentRepository.findById(22L)).thenReturn(Optional.of(second));
        when(actionRepository.findFirstByIncidentIdAndActionTypeAndStatusInOrderByRequestedAtDesc(
                eq(21L), eq("temporary_ip_block"), anyCollection()
        )).thenReturn(Optional.of(entity("pending-21", first, "PENDING_APPROVAL")));
        when(actionRepository.findFirstByIncidentIdAndActionTypeAndStatusInOrderByRequestedAtDesc(
                eq(22L), eq("temporary_ip_block"), anyCollection()
        )).thenReturn(Optional.of(entity("sent-22", second, "DISPATCHED")));
        when(actionService.approve(eq(21L), eq("pending-21"), any()))
                .thenReturn(action("pending-21", first, "DISPATCHED"));

        BulkDispatchResultDto result = service.dispatch(
                new BulkDispatchRequest(List.of(21L, 22L), "temporary_ip_block", "Bulk containment"),
                "analyst"
        );

        assertThat(result.dispatched()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.items()).extracting(BulkDispatchItemDto::outcome)
                .containsExactly("DISPATCHED", "SKIPPED");
    }

    @Test
    void isolatesInvalidIncidentInsteadOfFailingWholeBatch() {
        Incident invalid = incident(31L, "unknown");
        Incident valid = incident(32L, "203.0.113.32");
        when(incidentRepository.findById(31L)).thenReturn(Optional.of(invalid));
        when(incidentRepository.findById(32L)).thenReturn(Optional.of(valid));
        when(actionRepository.findFirstByIncidentIdAndActionTypeAndStatusInOrderByRequestedAtDesc(
                eq(32L), eq("temporary_ip_block"), anyCollection()
        )).thenReturn(Optional.empty());
        when(actionService.create(eq(32L), any())).thenReturn(action("action-32", valid, "PENDING_APPROVAL"));
        when(actionService.approve(eq(32L), eq("action-32"), any())).thenReturn(action("action-32", valid, "DISPATCHED"));

        BulkDispatchResultDto result = service.dispatch(
                new BulkDispatchRequest(List.of(31L, 32L), "temporary_ip_block", "Bulk containment"),
                "analyst"
        );

        assertThat(result.dispatched()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.items().get(0).message()).contains("no actionable source IP");
    }

    @Test
    void doesNotApprovePendingActionWithAClientSuppliedDifferentTarget() {
        Incident incident = incident(41L, "203.0.113.41");
        ResponseAction pending = entity("pending-41", incident, "PENDING_APPROVAL");
        pending.setTarget("203.0.113.99");
        when(incidentRepository.findById(41L)).thenReturn(Optional.of(incident));
        when(actionRepository.findFirstByIncidentIdAndActionTypeAndStatusInOrderByRequestedAtDesc(
                eq(41L), eq("temporary_ip_block"), anyCollection()
        )).thenReturn(Optional.of(pending));

        BulkDispatchResultDto result = service.dispatch(
                new BulkDispatchRequest(List.of(41L), "temporary_ip_block", "Bulk containment"),
                "analyst"
        );

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.items().get(0).message()).contains("does not match");
    }

    private Incident incident(long id, String sourceIp) {
        Incident incident = new Incident();
        incident.setId(id);
        incident.setTitle("Incident " + id);
        incident.setSourceIp(sourceIp);
        return incident;
    }

    private ResponseAction entity(String id, Incident incident, String status) {
        ResponseAction action = new ResponseAction();
        action.setId(id);
        action.setIncident(incident);
        action.setActionType("temporary_ip_block");
        action.setTarget(incident.getSourceIp());
        action.setRationale("Bulk containment");
        action.setStatus(status);
        action.setRequestedAt(Instant.now());
        return action;
    }

    private ResponseActionDto action(String id, Incident incident, String status) {
        return ResponseActionDto.from(entity(id, incident, status));
    }
}
