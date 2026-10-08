package com.socdss.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateResponseActionRequest(
        @NotBlank
        @Pattern(regexp = "temporary_ip_block|rate_limit_ip|session_invalidation|increase_monitoring|create_ticket")
        String actionType,
        @NotBlank @Size(max = 255) String target,
        @NotBlank @Size(max = 2000) String rationale,
        @Size(max = 255) String requestedBy
) {}
