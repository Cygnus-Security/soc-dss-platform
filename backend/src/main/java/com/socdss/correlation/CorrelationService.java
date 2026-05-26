package com.socdss.correlation;

import com.socdss.alert.SecurityAlert;
import com.socdss.alert.SecurityAlertRepository;
import com.socdss.incident.Incident;
import com.socdss.incident.IncidentRepository;
import com.socdss.recommendation.RecommendationService;
import com.socdss.risk.RiskAssessmentResult;
import com.socdss.risk.RiskAssessmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CorrelationService {
    private final SecurityAlertRepository alertRepository;
    private final IncidentRepository incidentRepository;
    private final RiskAssessmentService riskAssessmentService;
    private final RecommendationService recommendationService;

    public CorrelationService(SecurityAlertRepository alertRepository,
                              IncidentRepository incidentRepository,
                              RiskAssessmentService riskAssessmentService,
                              RecommendationService recommendationService) {
        this.alertRepository = alertRepository;
        this.incidentRepository = incidentRepository;
        this.riskAssessmentService = riskAssessmentService;
        this.recommendationService = recommendationService;
    }

    @Transactional
    public List<Incident> correlateAll() {
        incidentRepository.deleteAll();

        List<SecurityAlert> alerts = alertRepository.findAll();
        Map<String, List<SecurityAlert>> groups = new HashMap<>();

        for (SecurityAlert alert : alerts) {
            String key = correlationKey(alert);
            groups.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(alert);
        }

        List<Incident> incidents = groups.values().stream()
                .map(this::buildIncident)
                .sorted(Comparator.comparing(Incident::getRiskScore).reversed())
                .toList();

        return incidentRepository.saveAll(incidents);
    }

    private String correlationKey(SecurityAlert alert) {
        return safe(alert.getAgentName()) + "|" + safe(alert.getSourceIp()) + "|" + safe(alert.getIncidentType());
    }

    private Incident buildIncident(List<SecurityAlert> relatedAlerts) {
        SecurityAlert first = relatedAlerts.get(0);
        RiskAssessmentResult risk = riskAssessmentService.assess(relatedAlerts);

        Instant firstSeen = relatedAlerts.stream()
                .map(SecurityAlert::getEventTimestamp)
                .filter(v -> v != null)
                .min(Comparator.naturalOrder())
                .orElse(Instant.now());

        Instant lastSeen = relatedAlerts.stream()
                .map(SecurityAlert::getEventTimestamp)
                .filter(v -> v != null)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());

        int maxRuleLevel = relatedAlerts.stream()
                .map(SecurityAlert::getWazuhRuleLevel)
                .max(Comparator.naturalOrder())
                .orElse(0);

        Incident incident = new Incident();
        incident.setIncidentType(first.getIncidentType());
        incident.setTitle(first.getIncidentType() + " on " + safe(first.getAgentName()));
        incident.setSourceIp(first.getSourceIp());
        incident.setTargetAsset(first.getAgentName());
        incident.setAlertCount(relatedAlerts.size());
        incident.setMaxRuleLevel(maxRuleLevel);
        incident.setFirstSeen(firstSeen);
        incident.setLastSeen(lastSeen);
        incident.setMitreTactic(first.getMitreTactic());
        incident.setMitreTechnique(first.getMitreTechnique());
        incident.setRiskScore(risk.score());
        incident.setRiskLevel(risk.level());
        incident.setExplanation(risk.explanation());
        incident.setRecommendation(recommendationService.recommend(first.getIncidentType(), risk.level()));
        incident.getAlerts().addAll(relatedAlerts);
        return incident;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
