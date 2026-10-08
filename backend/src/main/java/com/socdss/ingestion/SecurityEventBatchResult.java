package com.socdss.ingestion;

public record SecurityEventBatchResult(
        int received,
        int imported,
        int duplicates
) {}
