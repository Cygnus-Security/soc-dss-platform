package com.socdss.response;

import java.util.List;

public record ResponseActionPageDto(
        List<ResponseActionDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {}
