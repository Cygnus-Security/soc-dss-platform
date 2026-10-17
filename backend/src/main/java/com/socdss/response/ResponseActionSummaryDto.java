package com.socdss.response;

import java.time.Instant;
import java.util.Map;

public record ResponseActionSummaryDto(
        long totalActions,
        long processedActions,
        long executedActions,
        long dryRunActions,
        long failedActions,
        long awaitingApprovalActions,
        long inProgressActions,
        long rejectedActions,
        Instant lastProcessedAt,
        Map<String, Long> actionsByStatus,
        Map<String, Long> actionsByType
) {}
