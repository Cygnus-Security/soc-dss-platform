package com.socdss.wazuh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socdss.alert.SecurityAlert;
import com.socdss.alert.SecurityAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class WazuhAlertImportService {
    private final SecurityAlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    public WazuhAlertImportService(SecurityAlertRepository alertRepository, ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.objectMapper = objectMapper;
    }

    public WazuhAlertImportResult importJsonLines(MultipartFile file) {
        int total = 0;
        int skipped = 0;
        List<SecurityAlert> alerts = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                total++;
                line = line.trim();
                if (line.isEmpty()) {
                    skipped++;
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(line);
                    SecurityAlert alert = toSecurityAlert(root, line);
                    if (alert.getWazuhRuleLevel() >= 3) {
                        alerts.add(alert);
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    skipped++;
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to import Wazuh alerts: " + e.getMessage(), e);
        }

        alertRepository.saveAll(alerts);
        return new WazuhAlertImportResult(total, alerts.size(), skipped);
    }

    private SecurityAlert toSecurityAlert(JsonNode root, String raw) {
        JsonNode rule = root.path("rule");
        JsonNode agent = root.path("agent");
        JsonNode data = root.path("data");
        JsonNode mitre = rule.path("mitre");

        String ruleId = text(rule, "id", "unknown");
        String description = text(rule, "description", "");
        String groups = rule.path("groups").toString();

        SecurityAlert alert = new SecurityAlert();
        alert.setSource("Wazuh");
        alert.setExternalId(text(root, "id", null));
        alert.setEventTimestamp(parseTimestamp(text(root, "timestamp", null)));
        alert.setWazuhRuleId(ruleId);
        alert.setWazuhRuleLevel(rule.path("level").asInt(0));
        alert.setAgentName(text(agent, "name", "unknown"));
        alert.setAgentIp(text(agent, "ip", null));
        alert.setSourceIp(firstNonBlank(
                text(data, "srcip", null),
                text(data, "src_ip", null),
                text(data, "src", null),
                text(root, "srcip", null),
                "unknown"
        ));
        alert.setDestinationIp(firstNonBlank(text(data, "dstip", null), text(data, "dst_ip", null), null));
        alert.setDescription(description);
        alert.setMitreTactic(firstArrayText(mitre.path("tactic")));
        alert.setMitreTechnique(firstArrayText(mitre.path("technique")));
        alert.setIncidentType(AlertClassifier.classify(ruleId, description, groups));
        alert.setRawJson(raw);
        return alert;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? defaultValue : text;
    }

    private String firstArrayText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isArray() && node.size() > 0) {
            return node.get(0).asText("");
        }
        return node.asText("");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        try {
            String normalized = timestamp.replace("Z", "+00:00");
            if (normalized.matches(".*[+-]\\d{4}$")) {
                normalized = normalized.substring(0, normalized.length() - 5)
                        + normalized.substring(normalized.length() - 5, normalized.length() - 2)
                        + ":"
                        + normalized.substring(normalized.length() - 2);
            }
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC);
            } catch (Exception ignoredAgain) {
                return Instant.now();
            }
        }
    }
}
