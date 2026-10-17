package com.socdss.response;

import com.socdss.incident.Incident;
import com.socdss.incident.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ResponseActionRepositoryTest {
    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private ResponseActionRepository actionRepository;

    @Test
    void searchesPagedActionsAndAggregatesSoarStatuses() {
        Incident incident = incidentRepository.saveAndFlush(incident());
        Instant older = Instant.parse("2026-10-17T08:40:00Z");
        Instant newer = Instant.parse("2026-10-17T08:46:00Z");
        actionRepository.saveAllAndFlush(List.of(
                action("11111111-1111-1111-1111-111111111111", incident, "FAILED", "192.0.2.10", older),
                action("22222222-2222-2222-2222-222222222222", incident, "DRY_RUN", "198.51.100.20", newer)
        ));

        Page<ResponseAction> failed = actionRepository.search(
                "FAILED", "", "192.0.2", PageRequest.of(0, 10)
        );
        Page<ResponseAction> unfiltered = actionRepository.search(
                "", "", "", PageRequest.of(0, 10)
        );
        Map<String, Long> counts = actionRepository.countGroupedByStatus().stream()
                .collect(Collectors.toMap(ResponseActionStatusCount::getStatus, ResponseActionStatusCount::getTotal));

        assertThat(failed.getTotalElements()).isEqualTo(1);
        assertThat(failed.getContent()).extracting(ResponseAction::getStatus).containsExactly("FAILED");
        assertThat(unfiltered.getTotalElements()).isEqualTo(2);
        assertThat(counts).containsEntry("FAILED", 1L).containsEntry("DRY_RUN", 1L);
        assertThat(actionRepository.findLatestCompletedAt()).isEqualTo(newer);
    }

    private Incident incident() {
        Incident incident = new Incident();
        incident.setTitle("SOAR test incident");
        incident.setIncidentType("Test Incident");
        incident.setRiskScore(80.0);
        incident.setRiskLevel("High");
        return incident;
    }

    private ResponseAction action(String id, Incident incident, String status, String target, Instant completedAt) {
        ResponseAction action = new ResponseAction();
        action.setId(id);
        action.setIncident(incident);
        action.setActionType("temporary_ip_block");
        action.setTarget(target);
        action.setRationale("Repository test");
        action.setStatus(status);
        action.setRequestedAt(completedAt.minusSeconds(30));
        action.setCompletedAt(completedAt);
        action.setResultMessage("SOAR result for " + target);
        return action;
    }
}
