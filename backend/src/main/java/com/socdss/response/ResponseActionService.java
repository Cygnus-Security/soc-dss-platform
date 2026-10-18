package com.socdss.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socdss.incident.Incident;
import com.socdss.incident.IncidentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ResponseActionService {
    private static final String CSV_HEADER = "action_id,incident_id,action_type,target,rationale,status,requested_by,requested_at,decided_by,decided_at,approval_expires_at,completed_at,result_message\n";
    private static final String CSV_FORMULA_PREFIXES = "=+-@";

    private final IncidentRepository incidentRepository;
    private final ResponseActionRepository actionRepository;
    private final ApprovalTokenService tokenService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String approvedTopic;
    private final boolean kafkaEnabled;
    private final long approvalTtlMinutes;

    public ResponseActionService(
            IncidentRepository incidentRepository,
            ResponseActionRepository actionRepository,
            ApprovalTokenService tokenService,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.approved-action-topic}") String approvedTopic,
            @Value("${app.kafka.enabled:false}") boolean kafkaEnabled,
            @Value("${app.response.approval-ttl-minutes:60}") long approvalTtlMinutes
    ) {
        this.incidentRepository = incidentRepository;
        this.actionRepository = actionRepository;
        this.tokenService = tokenService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.approvedTopic = approvedTopic;
        this.kafkaEnabled = kafkaEnabled;
        this.approvalTtlMinutes = approvalTtlMinutes;
    }

    @Transactional
    public ResponseActionDto create(Long incidentId, CreateResponseActionRequest request) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        ResponseAction action = new ResponseAction();
        action.setId(UUID.randomUUID().toString());
        action.setIncident(incident);
        action.setActionType(request.actionType());
        action.setTarget(request.target());
        action.setRationale(request.rationale());
        action.setRequestedBy(request.requestedBy());
        action.setRequestedAt(Instant.now());
        action.setStatus("PENDING_APPROVAL");
        return ResponseActionDto.from(actionRepository.save(action));
    }

    @Transactional(readOnly = true)
    public List<ResponseActionDto> list(Long incidentId) {
        return actionRepository.findByIncidentIdOrderByRequestedAtDesc(incidentId).stream()
                .map(ResponseActionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResponseActionPageDto page(int page, int size, String status, String actionType, String search) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 10), 100);
        Page<ResponseAction> result = actionRepository.search(
                normalize(status, true),
                normalize(actionType, false),
                normalize(search, false),
                PageRequest.of(safePage, safeSize)
        );
        return new ResponseActionPageDto(
                result.getContent().stream().map(ResponseActionDto::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    @Transactional(readOnly = true)
    public ResponseActionSummaryDto summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        actionRepository.countGroupedByStatus().stream()
                .sorted((left, right) -> left.getStatus().compareTo(right.getStatus()))
                .forEach(count -> byStatus.put(count.getStatus(), count.getTotal()));

        Map<String, Long> byType = new LinkedHashMap<>();
        actionRepository.countGroupedByActionType().stream()
                .sorted((left, right) -> left.getActionType().compareTo(right.getActionType()))
                .forEach(count -> byType.put(count.getActionType(), count.getTotal()));

        long executed = count(byStatus, "EXECUTED");
        long dryRun = count(byStatus, "DRY_RUN");
        long failed = count(byStatus, "FAILED");
        Instant lastProcessedAt = actionRepository.findLatestCompletedAt();
        return new ResponseActionSummaryDto(
                byStatus.values().stream().mapToLong(Long::longValue).sum(),
                executed + dryRun + failed,
                executed,
                dryRun,
                failed,
                count(byStatus, "PENDING_APPROVAL"),
                count(byStatus, "APPROVED") + count(byStatus, "DISPATCHED"),
                count(byStatus, "REJECTED"),
                lastProcessedAt,
                byStatus,
                byType
        );
    }

    @Transactional(readOnly = true)
    public String exportCsv(String status, String actionType, String search) {
        List<ResponseAction> actions = actionRepository.searchForExport(
                normalize(status, true),
                normalize(actionType, false),
                normalize(search, false)
        );
        StringBuilder csv = new StringBuilder("\uFEFF").append(CSV_HEADER);
        for (ResponseAction action : actions) {
            csv.append(csvCell(action.getId())).append(',')
                    .append(csvCell(action.getIncident().getId())).append(',')
                    .append(csvCell(action.getActionType())).append(',')
                    .append(csvCell(action.getTarget())).append(',')
                    .append(csvCell(action.getRationale())).append(',')
                    .append(csvCell(action.getStatus())).append(',')
                    .append(csvCell(action.getRequestedBy())).append(',')
                    .append(csvCell(action.getRequestedAt())).append(',')
                    .append(csvCell(action.getDecidedBy())).append(',')
                    .append(csvCell(action.getDecidedAt())).append(',')
                    .append(csvCell(action.getApprovalExpiresAt())).append(',')
                    .append(csvCell(action.getCompletedAt())).append(',')
                    .append(csvCell(action.getResultMessage())).append('\n');
        }
        return csv.toString();
    }

    @Transactional
    public ResponseActionDto approve(Long incidentId, String actionId, ActionDecisionRequest decision) {
        if (!kafkaEnabled) {
            throw new IllegalStateException("Kafka response workflow is disabled");
        }
        ResponseAction action = load(incidentId, actionId);
        requirePending(action);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(approvalTtlMinutes, ChronoUnit.MINUTES);
        action.setStatus("APPROVED");
        action.setDecidedBy(decision.actor());
        action.setDecidedAt(now);
        action.setApprovalExpiresAt(expiresAt);
        action.getIncident().setDecisionStatus("ACTION_APPROVED");
        actionRepository.save(action);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("schema_version", "1.0");
        message.put("action_id", action.getId());
        message.put("incident_id", action.getIncident().getId());
        message.put("action_type", action.getActionType());
        message.put("target", action.getTarget());
        message.put("approved_by", decision.actor());
        message.put("approved_at", now.toString());
        message.put("expires_at", expiresAt.toString());
        message.put("approval_token", tokenService.issue(action, expiresAt));
        message.put("rationale", action.getRationale());
        try {
            kafkaTemplate.send(approvedTopic, action.getId(), json(message)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to dispatch approved action", e);
        }
        action.setStatus("DISPATCHED");

        return ResponseActionDto.from(action);
    }

    @Transactional
    public ResponseActionDto reject(Long incidentId, String actionId, ActionDecisionRequest decision) {
        ResponseAction action = load(incidentId, actionId);
        requirePending(action);
        action.setStatus("REJECTED");
        action.setDecidedBy(decision.actor());
        action.setDecidedAt(Instant.now());
        action.setResultMessage(decision.reason());
        action.getIncident().setDecisionStatus("ACTION_REJECTED");
        return ResponseActionDto.from(action);
    }

    @Transactional
    public void applyResult(ActionResultV1 result, String rawPayload) {
        ResponseAction action = actionRepository.findById(result.actionId()).orElseGet(() -> automaticAction(result));
        if (action == null || isTerminal(action.getStatus())) {
            return;
        }
        action.setStatus(result.status());
        action.setResultMessage(result.message());
        action.setResultPayload(rawPayload);
        action.setCompletedAt(result.completedAt());
        action.getIncident().setDecisionStatus("ACTION_" + result.status());
    }

    private ResponseAction load(Long incidentId, String actionId) {
        ResponseAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found"));
        if (!action.getIncident().getId().equals(incidentId)) {
            throw new IllegalArgumentException("Action does not belong to incident");
        }
        return action;
    }

    private void requirePending(ResponseAction action) {
        if (!"PENDING_APPROVAL".equals(action.getStatus())) {
            throw new IllegalArgumentException("Action has already been decided");
        }
    }

    private boolean isTerminal(String status) {
        return "EXECUTED".equals(status) || "FAILED".equals(status)
                || "DRY_RUN".equals(status) || "REJECTED".equals(status);
    }

    private String normalize(String value, boolean uppercase) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        return uppercase ? normalized.toUpperCase(java.util.Locale.ROOT) : normalized;
    }

    private long count(Map<String, Long> counts, String key) {
        return counts.getOrDefault(key, 0L);
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && (CSV_FORMULA_PREFIXES.indexOf(text.charAt(0)) >= 0
                || text.charAt(0) == '\t' || text.charAt(0) == '\r')) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private ResponseAction automaticAction(ActionResultV1 result) {
        Incident incident = result.incidentId() == null
                ? incidentRepository.findFirstBySourceIpOrderByLastSeenDesc(result.target()).orElse(null)
                : incidentRepository.findById(result.incidentId()).orElse(null);
        if (incident == null) {
            return null;
        }
        ResponseAction action = new ResponseAction();
        action.setId(result.actionId());
        action.setIncident(incident);
        action.setActionType(result.actionType());
        action.setTarget(result.target());
        action.setRationale("Automatically dispatched by the SOAR deterministic policy");
        action.setStatus("DISPATCHED");
        action.setRequestedBy("SOAR_POLICY");
        action.setRequestedAt(Instant.now());
        action.setDecidedBy("SOAR_POLICY");
        action.setDecidedAt(Instant.now());
        return actionRepository.save(action);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize action", e);
        }
    }
}
