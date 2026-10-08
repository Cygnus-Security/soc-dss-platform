package com.socdss.ingestion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SecurityEventBatchRequest(
        @NotEmpty
        @Size(max = 1000)
        List<@Valid SecurityEventV1> events
) {}
