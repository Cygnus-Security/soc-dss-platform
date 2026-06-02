package com.socdss.incident;

import com.socdss.correlation.CorrelationService;
import com.socdss.correlation.CorrelationJobStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {
    private final IncidentRepository incidentRepository;
    private final CorrelationService correlationService;

    public IncidentController(IncidentRepository incidentRepository, CorrelationService correlationService) {
        this.incidentRepository = incidentRepository;
        this.correlationService = correlationService;
    }

    @GetMapping
    public List<IncidentDto> list() {
        return incidentRepository.findAllByOrderByRiskScoreDesc().stream()
                .map(i -> IncidentDto.from(i, false))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentDto> detail(@PathVariable Long id) {
        return incidentRepository.findById(id)
                .map(i -> ResponseEntity.ok(IncidentDto.from(i, true)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/correlate")
    public ResponseEntity<CorrelationJobStatus> correlate() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(correlationService.startCorrelationJob());
    }

    @GetMapping("/correlation/status")
    public CorrelationJobStatus correlationStatus() {
        return correlationService.jobStatus();
    }
}
