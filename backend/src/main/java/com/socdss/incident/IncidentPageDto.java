package com.socdss.incident;

import java.util.List;

public record IncidentPageDto(
        List<IncidentDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {}
