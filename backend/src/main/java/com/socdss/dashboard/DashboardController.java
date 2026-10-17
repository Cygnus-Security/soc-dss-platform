package com.socdss.dashboard;

import com.socdss.alert.SecurityAlertRepository;
import com.socdss.incident.IncidentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final SecurityAlertRepository alertRepository;
    private final IncidentRepository incidentRepository;

    public DashboardController(SecurityAlertRepository alertRepository, IncidentRepository incidentRepository) {
        this.alertRepository = alertRepository;
        this.incidentRepository = incidentRepository;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        long alerts = alertRepository.count();
        Map<String, Long> byLevel = incidentRepository.countGroupedByRiskLevel().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getRiskLevel() == null ? "Unknown" : entry.getRiskLevel(),
                        entry -> entry.getTotal()
                ));
        long incidents = byLevel.values().stream().mapToLong(Long::longValue).sum();
        double reduction = alerts > 0 ? ((alerts - incidents) * 100.0 / alerts) : 0.0;

        return new DashboardSummary(
                alerts,
                incidents,
                byLevel.getOrDefault("Critical", 0L),
                byLevel.getOrDefault("High", 0L),
                Math.round(reduction * 100.0) / 100.0,
                byLevel
        );
    }
}
