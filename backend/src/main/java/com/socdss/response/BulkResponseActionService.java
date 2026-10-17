package com.socdss.response;

import com.socdss.incident.Incident;
import com.socdss.incident.IncidentRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class BulkResponseActionService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING_APPROVAL", "APPROVED", "DISPATCHED");

    private final IncidentRepository incidentRepository;
    private final ResponseActionRepository actionRepository;
    private final ResponseActionService actionService;

    public BulkResponseActionService(IncidentRepository incidentRepository,
                                     ResponseActionRepository actionRepository,
                                     ResponseActionService actionService) {
        this.incidentRepository = incidentRepository;
        this.actionRepository = actionRepository;
        this.actionService = actionService;
    }

    /**
     * Deliberately not transactional: every incident is committed independently so a
     * single invalid incident or Kafka failure does not roll back the whole batch.
     */
    public BulkDispatchResultDto dispatch(BulkDispatchRequest request, String actor) {
        List<Long> incidentIds = new LinkedHashSet<>(request.incidentIds()).stream().toList();
        List<BulkDispatchItemDto> items = incidentIds.stream()
                .map(incidentId -> dispatchOne(incidentId, request, actor))
                .toList();

        return new BulkDispatchResultDto(
                incidentIds.size(),
                count(items, "DISPATCHED"),
                count(items, "SKIPPED"),
                count(items, "FAILED"),
                items
        );
    }

    private BulkDispatchItemDto dispatchOne(Long incidentId, BulkDispatchRequest request, String actor) {
        if (incidentId == null) {
            return failed(null, null, null, "Incident ID is required");
        }

        Optional<Incident> incidentResult = incidentRepository.findById(incidentId);
        if (incidentResult.isEmpty()) {
            return failed(incidentId, null, null, "Incident not found");
        }

        Incident incident = incidentResult.get();
        String target = incident.getSourceIp();
        if (target == null || target.isBlank() || "unknown".equalsIgnoreCase(target.trim())) {
            return failed(incidentId, null, target, "Incident has no actionable source IP");
        }

        String actionId = null;
        try {
            Optional<ResponseAction> activeAction = actionRepository
                    .findFirstByIncidentIdAndActionTypeAndStatusInOrderByRequestedAtDesc(
                            incidentId,
                            request.actionType(),
                            ACTIVE_STATUSES
                    );

            if (activeAction.isPresent()) {
                ResponseAction existing = activeAction.get();
                actionId = existing.getId();
                if (!"PENDING_APPROVAL".equals(existing.getStatus())) {
                    return new BulkDispatchItemDto(
                            incidentId,
                            existing.getId(),
                            existing.getTarget(),
                            "SKIPPED",
                            "An active action is already approved or dispatched"
                    );
                }
                if (!target.trim().equals(existing.getTarget())) {
                    return failed(
                            incidentId,
                            existing.getId(),
                            existing.getTarget(),
                            "Pending action target does not match the incident source IP"
                    );
                }

                ResponseActionDto dispatched = actionService.approve(
                        incidentId,
                        existing.getId(),
                        new ActionDecisionRequest(actor, "Bulk approval")
                );
                return dispatched(dispatched, "Existing pending action approved and dispatched");
            }

            ResponseActionDto created = actionService.create(
                    incidentId,
                    new CreateResponseActionRequest(
                            request.actionType(),
                            target.trim(),
                            request.rationale(),
                            actor
                    )
            );
            actionId = created.id();
            ResponseActionDto dispatched = actionService.approve(
                    incidentId,
                    created.id(),
                    new ActionDecisionRequest(actor, "Bulk approval")
            );
            return dispatched(dispatched, "Action created, approved and dispatched");
        } catch (RuntimeException exception) {
            return failed(incidentId, actionId, target, rootMessage(exception));
        }
    }

    private BulkDispatchItemDto dispatched(ResponseActionDto action, String message) {
        return new BulkDispatchItemDto(
                action.incidentId(),
                action.id(),
                action.target(),
                "DISPATCHED",
                message
        );
    }

    private BulkDispatchItemDto failed(Long incidentId, String actionId, String target, String message) {
        return new BulkDispatchItemDto(incidentId, actionId, target, "FAILED", message);
    }

    private int count(List<BulkDispatchItemDto> items, String outcome) {
        return (int) items.stream().filter(item -> outcome.equals(item.outcome())).count();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? "Unable to dispatch action" : message;
    }
}
