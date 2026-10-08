package com.socdss.ingestion;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class SecurityEventIngestionController {
    private final SecurityEventIngestionService ingestionService;

    public SecurityEventIngestionController(SecurityEventIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/batch")
    public SecurityEventBatchResult ingestBatch(@Valid @RequestBody SecurityEventBatchRequest request) {
        return ingestionService.ingest(request.events());
    }
}
