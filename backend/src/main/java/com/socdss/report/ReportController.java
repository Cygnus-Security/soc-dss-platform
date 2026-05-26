package com.socdss.report;

import com.socdss.incident.Incident;
import com.socdss.incident.IncidentRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final IncidentRepository incidentRepository;

    public ReportController(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @GetMapping("/incidents.csv")
    public ResponseEntity<String> incidentsCsv() {
        List<Incident> incidents = incidentRepository.findAllByOrderByRiskScoreDesc();
        StringBuilder csv = new StringBuilder();
        csv.append("id,title,incident_type,source_ip,target_asset,alert_count,max_rule_level,risk_score,risk_level,recommendation\n");
        for (Incident i : incidents) {
            csv.append(i.getId()).append(',')
                    .append(q(i.getTitle())).append(',')
                    .append(q(i.getIncidentType())).append(',')
                    .append(q(i.getSourceIp())).append(',')
                    .append(q(i.getTargetAsset())).append(',')
                    .append(i.getAlertCount()).append(',')
                    .append(i.getMaxRuleLevel()).append(',')
                    .append(i.getRiskScore()).append(',')
                    .append(q(i.getRiskLevel())).append(',')
                    .append(q(i.getRecommendation())).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=incidents.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private String q(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
