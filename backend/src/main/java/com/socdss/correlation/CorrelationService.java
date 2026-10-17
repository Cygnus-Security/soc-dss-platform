package com.socdss.correlation;

import com.socdss.alert.CorrelationAlertView;
import com.socdss.alert.SecurityAlert;
import com.socdss.alert.SecurityAlertRepository;
import com.socdss.incident.Incident;
import com.socdss.incident.IncidentRepository;
import com.socdss.recommendation.RecommendationService;
import com.socdss.risk.RiskAssessmentResult;
import com.socdss.risk.RiskAssessmentService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CorrelationService {
    private static final Logger log = LoggerFactory.getLogger(CorrelationService.class);
    private static final int RELATED_ALERT_SAMPLE_LIMIT = 100;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<CorrelationJobStatus> jobStatus = new AtomicReference<>(
            new CorrelationJobStatus("IDLE", null, null, null, "No correlation job has run yet.")
    );

    private final SecurityAlertRepository alertRepository;
    private final IncidentRepository incidentRepository;
    private final RiskAssessmentService riskAssessmentService;
    private final RecommendationService recommendationService;
    private final TransactionTemplate transactionTemplate;
    private final long timeWindowMinutes;
    private final int readPageSize;
    private final int writeBatchSize;

    public CorrelationService(SecurityAlertRepository alertRepository,
                              IncidentRepository incidentRepository,
                              RiskAssessmentService riskAssessmentService,
                              RecommendationService recommendationService,
                              PlatformTransactionManager transactionManager,
                              @Value("${app.correlation.time-window-minutes:15}") long timeWindowMinutes,
                              @Value("${app.correlation.read-page-size:5000}") int readPageSize,
                              @Value("${app.correlation.write-batch-size:100}") int writeBatchSize) {
        this.alertRepository = alertRepository;
        this.incidentRepository = incidentRepository;
        this.riskAssessmentService = riskAssessmentService;
        this.recommendationService = recommendationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.timeWindowMinutes = timeWindowMinutes;
        this.readPageSize = Math.max(readPageSize, 100);
        this.writeBatchSize = Math.max(writeBatchSize, 1);
    }

    public synchronized CorrelationJobStatus startCorrelationJob() {
        CorrelationJobStatus current = jobStatus.get();
        if ("RUNNING".equals(current.status())) {
            return current;
        }

        Instant startedAt = Instant.now();
        CorrelationJobStatus started = running(startedAt, "Preparing correlation job.");
        jobStatus.set(started);
        executor.submit(() -> runCorrelation(startedAt));
        return started;
    }

    public CorrelationJobStatus jobStatus() {
        return jobStatus.get();
    }

    private void runCorrelation(Instant startedAt) {
        try {
            int incidentCount = correlateInBatches(startedAt);
            jobStatus.set(new CorrelationJobStatus(
                    "COMPLETED",
                    startedAt,
                    Instant.now(),
                    incidentCount,
                    String.format(Locale.ROOT, "Correlation completed successfully with %,d incidents.", incidentCount)
            ));
            log.info("Correlation completed: {} incidents", incidentCount);
        } catch (Throwable error) {
            String detail = error.getMessage() == null || error.getMessage().isBlank()
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
            jobStatus.set(new CorrelationJobStatus(
                    "FAILED",
                    startedAt,
                    Instant.now(),
                    null,
                    detail
            ));
            log.error("Correlation failed", error);
        }
    }

    private int correlateInBatches(Instant startedAt) {
        long totalAlerts = alertRepository.count();
        Map<String, CorrelationGroup> groups = new HashMap<>();
        Pageable page = PageRequest.of(0, readPageSize);
        long processedAlerts = 0;
        long lastId = 0;

        log.info("Correlation started: {} alerts, read page size {}, write batch size {}",
                totalAlerts, readPageSize, writeBatchSize);

        while (true) {
            List<CorrelationAlertView> alerts = alertRepository.findCorrelationBatchAfterId(lastId, page);
            if (alerts.isEmpty()) {
                break;
            }

            for (CorrelationAlertView alert : alerts) {
                groups.computeIfAbsent(correlationKey(alert), ignored -> new CorrelationGroup()).add(alert);
            }

            processedAlerts += alerts.size();
            lastId = alerts.get(alerts.size() - 1).getId();
            String progress = String.format(
                    Locale.ROOT,
                    "Grouping alerts: %,d / %,d (%.1f%%); %,d incident groups found.",
                    processedAlerts,
                    totalAlerts,
                    percent(processedAlerts, totalAlerts),
                    groups.size()
            );
            jobStatus.set(running(startedAt, progress));
            log.info(progress);

            if (alerts.size() < readPageSize) {
                break;
            }
        }

        List<Map.Entry<String, CorrelationGroup>> entries = new ArrayList<>(groups.entrySet());
        int saved = 0;
        for (int offset = 0; offset < entries.size(); offset += writeBatchSize) {
            int end = Math.min(offset + writeBatchSize, entries.size());
            List<Map.Entry<String, CorrelationGroup>> batch = entries.subList(offset, end);
            persistBatch(batch);
            saved += batch.size();

            String progress = String.format(
                    Locale.ROOT,
                    "Saving incidents: %,d / %,d (%.1f%%).",
                    saved,
                    entries.size(),
                    percent(saved, entries.size())
            );
            jobStatus.set(running(startedAt, progress));
            log.info(progress);
        }
        return saved;
    }

    private void persistBatch(List<Map.Entry<String, CorrelationGroup>> batch) {
        transactionTemplate.executeWithoutResult(status -> {
            List<String> correlationKeys = batch.stream().map(Map.Entry::getKey).toList();
            Map<String, Incident> existingIncidents = incidentRepository
                    .findAllWithAlertsByCorrelationKeyIn(correlationKeys)
                    .stream()
                    .collect(Collectors.toMap(Incident::getCorrelationKey, Function.identity(), (first, ignored) -> first));

            Set<Long> sampleIds = batch.stream()
                    .flatMap(entry -> entry.getValue().sampleAlertIds.stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<Long, SecurityAlert> sampleAlerts = alertRepository.findAllById(sampleIds)
                    .stream()
                    .collect(Collectors.toMap(SecurityAlert::getId, Function.identity()));

            List<Incident> incidents = new ArrayList<>(batch.size());
            for (Map.Entry<String, CorrelationGroup> entry : batch) {
                CorrelationGroup group = entry.getValue();
                SecurityAlert representative = sampleAlerts.get(group.representativeAlertId);
                if (representative == null) {
                    throw new IllegalStateException("Representative alert not found: " + group.representativeAlertId);
                }
                Incident incident = existingIncidents.getOrDefault(entry.getKey(), new Incident());
                incidents.add(buildIncident(entry.getKey(), group, representative, incident, sampleAlerts));
            }

            incidentRepository.saveAll(incidents);
            incidentRepository.flush();
        });
    }

    private Incident buildIncident(String correlationKey,
                                   CorrelationGroup group,
                                   SecurityAlert representative,
                                   Incident incident,
                                   Map<Long, SecurityAlert> sampleAlerts) {
        RiskAssessmentResult risk = riskAssessmentService.assess(
                representative,
                group.alertCount,
                group.maxRuleLevel,
                group.vulnerabilityContext
        );

        incident.setCorrelationKey(correlationKey);
        incident.setIncidentType(representative.getIncidentType());
        incident.setTitle(representative.getIncidentType() + " on " + safe(representative.getAgentName()));
        incident.setSourceIp(representative.getSourceIp());
        incident.setTargetAsset(representative.getAgentName());
        incident.setAlertCount(group.alertCount);
        incident.setMaxRuleLevel(group.maxRuleLevel);
        incident.setFirstSeen(group.firstSeen == null ? Instant.now() : group.firstSeen);
        incident.setLastSeen(group.lastSeen == null ? Instant.now() : group.lastSeen);
        incident.setMitreTactic(representative.getMitreTactic());
        incident.setMitreTechnique(representative.getMitreTechnique());
        incident.setRiskScore(risk.score());
        incident.setRiskLevel(risk.level());
        incident.setExplanation(risk.explanation());
        incident.setRecommendation(recommendationService.recommend(
                representative.getIncidentType(),
                risk.level(),
                representative.getMitreTactic(),
                representative.getAgentName()
        ));
        incident.getAlerts().clear();
        group.sampleAlertIds.stream()
                .map(sampleAlerts::get)
                .filter(alert -> alert != null)
                .forEach(incident.getAlerts()::add);
        return incident;
    }

    private String correlationKey(CorrelationAlertView alert) {
        long window = correlationWindow(alert.getEventTimestamp(), alert.getCreatedAt());
        return safe(alert.getAgentName()) + "|" + safe(alert.getSourceIp()) + "|" + safe(alert.getIncidentType()) + "|" + window;
    }

    private long correlationWindow(Instant timestamp, Instant createdAt) {
        long windowSeconds = Math.max(timeWindowMinutes, 1) * 60;
        Instant effectiveTimestamp = timestamp != null ? timestamp : createdAt;
        long epochSecond = effectiveTimestamp == null ? 0 : effectiveTimestamp.getEpochSecond();
        return epochSecond / windowSeconds;
    }

    private CorrelationJobStatus running(Instant startedAt, String message) {
        return new CorrelationJobStatus("RUNNING", startedAt, null, null, message);
    }

    private double percent(long completed, long total) {
        return total == 0 ? 100.0 : Math.min((completed * 100.0) / total, 100.0);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    private static final class CorrelationGroup {
        private Long representativeAlertId;
        private int alertCount;
        private int maxRuleLevel;
        private Instant firstSeen;
        private Instant lastSeen;
        private boolean vulnerabilityContext;
        private final List<Long> sampleAlertIds = new ArrayList<>(RELATED_ALERT_SAMPLE_LIMIT);

        private void add(CorrelationAlertView alert) {
            if (representativeAlertId == null) {
                representativeAlertId = alert.getId();
            }
            alertCount++;
            maxRuleLevel = Math.max(maxRuleLevel, alert.getWazuhRuleLevel() == null ? 0 : alert.getWazuhRuleLevel());

            Instant timestamp = alert.getEventTimestamp();
            if (timestamp != null) {
                firstSeen = firstSeen == null || timestamp.isBefore(firstSeen) ? timestamp : firstSeen;
                lastSeen = lastSeen == null || timestamp.isAfter(lastSeen) ? timestamp : lastSeen;
            }

            if (sampleAlertIds.size() < RELATED_ALERT_SAMPLE_LIMIT && alert.getId() != null) {
                sampleAlertIds.add(alert.getId());
            }

            String context = ((alert.getDescription() == null ? "" : alert.getDescription()) + " " +
                    (alert.getIncidentType() == null ? "" : alert.getIncidentType())).toLowerCase(Locale.ROOT);
            vulnerabilityContext = vulnerabilityContext || context.contains("cve") || context.contains("vulnerab");
        }
    }
}
