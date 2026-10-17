package com.socdss.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkDispatchRequest(
        @NotEmpty
        @Size(max = 100)
        List<Long> incidentIds,
        @NotBlank
        @Pattern(regexp = "temporary_ip_block")
        String actionType,
        @NotBlank
        @Size(max = 2000)
        String rationale
) {}
