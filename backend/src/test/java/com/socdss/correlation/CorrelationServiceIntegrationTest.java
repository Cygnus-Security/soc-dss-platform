package com.socdss.correlation;

import com.socdss.alert.SecurityAlert;
import com.socdss.alert.SecurityAlertRepository;
import com.socdss.incident.IncidentRepository;
import com.socdss.recommendation.RecommendationService;
import com.socdss.risk.RiskAssessmentResult;
import com.socdss.risk.RiskAssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
@Import(CorrelationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CorrelationServiceIntegrationTest {
    @Autowired
    private CorrelationService correlationService;

    @Autowired
    private SecurityAlertRepository alertRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @MockBean
    private RiskAssessmentService riskAssessmentService;

    @MockBean
    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        incidentRepository.deleteAll();
        alertRepository.deleteAll();
        when(riskAssessmentService.assess(anyList()))
                .thenReturn(new RiskAssessmentResult(75.0, "High", "Test risk assessment"));
        when(recommendationService.recommend(any(), any(), any(), any()))
                .thenReturn("Test recommendation");
    }

    @Test
    void canCorrelateAnExistingIncidentAgainInTheBackgroundThread() throws InterruptedException {
        alertRepository.saveAndFlush(alert());

        correlationService.startCorrelationJob();
        CorrelationJobStatus firstRun = awaitTerminalStatus();

        correlationService.startCorrelationJob();
        CorrelationJobStatus secondRun = awaitTerminalStatus();

        assertThat(firstRun.status()).isEqualTo("COMPLETED");
        assertThat(secondRun.status()).isEqualTo("COMPLETED");
        assertThat(secondRun.incidentCount()).isEqualTo(1);
        assertThat(incidentRepository.count()).isEqualTo(1);
    }

    private CorrelationJobStatus awaitTerminalStatus() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        CorrelationJobStatus status = correlationService.jobStatus();
        while ("RUNNING".equals(status.status()) && Instant.now().isBefore(deadline)) {
            Thread.sleep(25);
            status = correlationService.jobStatus();
        }
        return status;
    }

    private SecurityAlert alert() {
        SecurityAlert alert = new SecurityAlert();
        alert.setEventTimestamp(Instant.parse("2026-10-17T06:00:00Z"));
        alert.setSource("Wazuh");
        alert.setWazuhRuleLevel(12);
        alert.setAgentName("demo-server");
        alert.setSourceIp("192.0.2.10");
        alert.setIncidentType("Web Attack");
        alert.setDescription("Attack alert for correlation regression test");
        return alert;
    }
}
