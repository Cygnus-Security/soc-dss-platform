package com.socdss.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActionDecisionRequest(
        @NotBlank @Size(max = 255) String actor,
        @Size(max = 2000) String reason
) {}
