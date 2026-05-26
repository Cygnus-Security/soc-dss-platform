package com.socdss.risk;

import com.socdss.alert.SecurityAlert;
import com.socdss.asset.Asset;
import com.socdss.asset.AssetRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class RiskAssessmentService {
    private final AssetRepository assetRepository;

    private static final Map<String, Integer> CRITICALITY_SCORE = Map.of(
            "Low", 25,
            "Medium", 50,
            "High", 75,
            "Critical", 100
    );

    private static final Map<String, Integer> EXPOSURE_SCORE = Map.of(
            "Internal", 50,
            "Public", 100
    );

    private static final Map<String, Integer> TACTIC_WEIGHT = Map.ofEntries(
            Map.entry("Reconnaissance", 45),
            Map.entry("Initial Access", 75),
            Map.entry("Execution", 80),
            Map.entry("Persistence", 80),
            Map.entry("Privilege Escalation", 85),
            Map.entry("Defense Evasion", 85),
            Map.entry("Credential Access", 90),
            Map.entry("Discovery", 50),
            Map.entry("Lateral Movement", 85),
            Map.entry("Collection", 70),
            Map.entry("Command and Control", 90),
            Map.entry("Exfiltration", 95),
            Map.entry("Impact", 95)
    );

    public RiskAssessmentService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public RiskAssessmentResult assess(List<SecurityAlert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return new RiskAssessmentResult(0, "Low", "No alerts available for risk assessment.");
        }

        SecurityAlert representative = alerts.get(0);
        AssetContext asset = resolveAsset(representative);

        int maxRuleLevel = alerts.stream().map(SecurityAlert::getWazuhRuleLevel).max(Comparator.naturalOrder()).orElse(0);
        double severityScore = Math.min((maxRuleLevel / 16.0) * 100.0, 100.0);
        double assetScore = CRITICALITY_SCORE.getOrDefault(asset.criticality(), 50);
        double frequencyScore = Math.min(alerts.size() * 10.0, 100.0);
        double mitreScore = TACTIC_WEIGHT.getOrDefault(blankToDefault(representative.getMitreTactic(), ""), 35);
        double exposureScore = EXPOSURE_SCORE.getOrDefault(asset.exposure(), 50);
        double vulnerabilityScore = containsVulnerabilityContext(alerts) ? 80.0 : 0.0;

        double score =
                0.30 * severityScore +
                0.20 * assetScore +
                0.15 * frequencyScore +
                0.15 * mitreScore +
                0.10 * exposureScore +
                0.10 * vulnerabilityScore;

        double rounded = Math.round(score * 100.0) / 100.0;
        String level = riskLevel(rounded);

        String explanation = String.format(
                "Risk score %.2f was calculated using severity %.1f, asset criticality %s, frequency %.1f, MITRE tactic %s, exposure %s and vulnerability context %s.",
                rounded,
                severityScore,
                asset.criticality(),
                frequencyScore,
                blankToDefault(representative.getMitreTactic(), "N/A"),
                asset.exposure(),
                vulnerabilityScore > 0 ? "present" : "not detected"
        );

        return new RiskAssessmentResult(rounded, level, explanation);
    }

    private AssetContext resolveAsset(SecurityAlert alert) {
        if (alert.getAgentName() != null) {
            return assetRepository.findByName(alert.getAgentName())
                    .map(a -> new AssetContext(a.getCriticality(), a.getExposure()))
                    .orElseGet(() -> findByIp(alert));
        }
        return findByIp(alert);
    }

    private AssetContext findByIp(SecurityAlert alert) {
        if (alert.getAgentIp() != null) {
            return assetRepository.findByIpAddress(alert.getAgentIp())
                    .map(a -> new AssetContext(a.getCriticality(), a.getExposure()))
                    .orElse(new AssetContext("Medium", "Internal"));
        }
        return new AssetContext("Medium", "Internal");
    }

    private boolean containsVulnerabilityContext(List<SecurityAlert> alerts) {
        return alerts.stream().anyMatch(a -> {
            String text = ((a.getDescription() == null ? "" : a.getDescription()) + " " +
                    (a.getIncidentType() == null ? "" : a.getIncidentType())).toLowerCase();
            return text.contains("cve") || text.contains("vulnerab");
        });
    }

    private String riskLevel(double score) {
        if (score >= 80) return "Critical";
        if (score >= 60) return "High";
        if (score >= 40) return "Medium";
        return "Low";
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record AssetContext(String criticality, String exposure) {}
}
