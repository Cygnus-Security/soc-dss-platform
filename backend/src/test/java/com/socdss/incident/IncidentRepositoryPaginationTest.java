package com.socdss.incident;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class IncidentRepositoryPaginationTest {
    @Autowired
    private IncidentRepository incidentRepository;

    @Test
    void returnsStableRiskOrderedPagesAndGroupedCounts() {
        incidentRepository.saveAllAndFlush(java.util.List.of(
                incident("High one", 82.0, "High"),
                incident("Low one", 21.0, "Low"),
                incident("High two", 75.0, "High")
        ));

        Page<Incident> firstPage = incidentRepository.findAllByOrderByRiskScoreDescIdDesc(PageRequest.of(0, 2));
        Map<String, Long> counts = incidentRepository.countGroupedByRiskLevel().stream()
                .collect(Collectors.toMap(RiskLevelCount::getRiskLevel, RiskLevelCount::getTotal));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).extracting(Incident::getRiskScore).containsExactly(82.0, 75.0);
        assertThat(counts).containsEntry("High", 2L).containsEntry("Low", 1L);
    }

    private Incident incident(String title, double score, String level) {
        Incident incident = new Incident();
        incident.setTitle(title);
        incident.setIncidentType("Test Incident");
        incident.setRiskScore(score);
        incident.setRiskLevel(level);
        return incident;
    }
}
