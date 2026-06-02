package com.socdss.report;

import com.socdss.decision.DecisionAdvice;
import com.socdss.decision.DecisionSupportService;
import com.socdss.incident.Incident;
import com.socdss.incident.IncidentRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final IncidentRepository incidentRepository;
    private final DecisionSupportService decisionSupportService;

    public ReportController(IncidentRepository incidentRepository, DecisionSupportService decisionSupportService) {
        this.incidentRepository = incidentRepository;
        this.decisionSupportService = decisionSupportService;
    }

    @GetMapping("/incidents.csv")
    public ResponseEntity<String> incidentsCsv(@RequestParam(required = false) String period,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
        TimeRange range = resolveRange(period, from, to);
        List<Incident> incidents = range == null
                ? incidentRepository.findAllByOrderByRiskScoreDesc()
                : incidentRepository.findByCreatedAtBetweenOrderByRiskScoreDesc(range.from(), range.to());
        StringBuilder csv = new StringBuilder();
        csv.append("id,title,incident_type,created_at,source_ip,target_asset,alert_count,max_rule_level,risk_score,risk_level,decision_priority,sla_hours,confidence,escalation,analyst_verdict,decision_status,recommendation\n");
        for (Incident i : incidents) {
            DecisionAdvice advice = decisionSupportService.advise(i);
            csv.append(i.getId()).append(',')
                    .append(q(i.getTitle())).append(',')
                    .append(q(i.getIncidentType())).append(',')
                    .append(q(String.valueOf(i.getCreatedAt()))).append(',')
                    .append(q(i.getSourceIp())).append(',')
                    .append(q(i.getTargetAsset())).append(',')
                    .append(i.getAlertCount()).append(',')
                    .append(i.getMaxRuleLevel()).append(',')
                    .append(i.getRiskScore()).append(',')
                    .append(q(i.getRiskLevel())).append(',')
                    .append(q(advice.priority())).append(',')
                    .append(advice.slaHours()).append(',')
                    .append(advice.confidence()).append(',')
                    .append(q(advice.escalation())).append(',')
                    .append(q(i.getAnalystVerdict())).append(',')
                    .append(q(i.getDecisionStatus())).append(',')
                    .append(q(i.getRecommendation())).append('\n');
        }
        String filename = range == null ? "incidents.csv" : "incidents-" + range.label() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private TimeRange resolveRange(String period, String from, String to) {
        if (from != null && to != null) {
            return new TimeRange(LocalDate.parse(from).atStartOfDay().toInstant(ZoneOffset.UTC),
                    LocalDate.parse(to).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
                    from + "-to-" + to);
        }
        if (period == null || period.isBlank()) {
            return null;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = switch (period.toLowerCase()) {
            case "week" -> today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case "month" -> today.withDayOfMonth(1);
            case "year" -> today.withDayOfYear(1);
            default -> null;
        };
        if (start == null) {
            return null;
        }
        return new TimeRange(start.atStartOfDay().toInstant(ZoneOffset.UTC),
                today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
                period.toLowerCase());
    }

    private String q(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private record TimeRange(Instant from, Instant to, String label) {}
}
