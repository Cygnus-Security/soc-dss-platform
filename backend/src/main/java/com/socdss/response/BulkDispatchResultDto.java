package com.socdss.response;

import java.util.List;

public record BulkDispatchResultDto(
        int requested,
        int dispatched,
        int skipped,
        int failed,
        List<BulkDispatchItemDto> items
) {}
