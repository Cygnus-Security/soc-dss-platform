package com.socdss.response;

public record BulkDispatchItemDto(
        Long incidentId,
        String actionId,
        String target,
        String outcome,
        String message
) {}
