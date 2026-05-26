package com.socdss.recommendation;

import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
    public String recommend(String incidentType, String riskLevel) {
        String type = incidentType == null ? "" : incidentType;
        boolean highRisk = "High".equalsIgnoreCase(riskLevel) || "Critical".equalsIgnoreCase(riskLevel);

        if (type.contains("SSH Brute Force")) {
            return highRisk
                    ? "Block source IP, check successful login events, reset affected account if needed, and escalate to L2 analyst."
                    : "Monitor source IP and review authentication logs for repeated failures.";
        }
        if (type.contains("File Integrity")) {
            return highRisk
                    ? "Collect evidence, verify unauthorized change, compare with approved change request, and isolate host if webshell or sensitive file compromise is confirmed."
                    : "Verify file change and correlate with maintenance/change management records.";
        }
        if (type.contains("Vulnerability")) {
            return highRisk
                    ? "Prioritize patching, reduce exposure, apply temporary mitigation, and create a remediation ticket."
                    : "Schedule patching according to the maintenance window and monitor for exploitation attempts.";
        }
        if (type.contains("Web Attack")) {
            return highRisk
                    ? "Block attacker IP if confirmed, review web access logs, validate application vulnerability, and escalate the incident."
                    : "Monitor web request pattern and tune detection rules if false positives are observed.";
        }
        if (type.contains("Reconnaissance")) {
            return highRisk
                    ? "Increase monitoring on the target asset, block persistent scanning sources, and check for follow-up exploitation attempts."
                    : "Monitor scanning behavior and correlate with subsequent attack attempts.";
        }
        return highRisk
                ? "Escalate to SOC analyst, collect related logs, enrich alert context, and start incident triage."
                : "Monitor and enrich alert context before escalation.";
    }
}
