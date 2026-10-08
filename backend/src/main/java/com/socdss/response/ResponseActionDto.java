package com.socdss.response;

import java.time.Instant;

public record ResponseActionDto(
        String id,
        Long incidentId,
        String actionType,
        String target,
        String rationale,
        String status,
        String requestedBy,
        String decidedBy,
        Instant requestedAt,
        Instant decidedAt,
        Instant approvalExpiresAt,
        String resultMessage,
        Instant completedAt
) {
    public static ResponseActionDto from(ResponseAction action) {
        return new ResponseActionDto(
                action.getId(), action.getIncident().getId(), action.getActionType(), action.getTarget(),
                action.getRationale(), action.getStatus(), action.getRequestedBy(), action.getDecidedBy(),
                action.getRequestedAt(), action.getDecidedAt(), action.getApprovalExpiresAt(),
                action.getResultMessage(), action.getCompletedAt()
        );
    }
}
