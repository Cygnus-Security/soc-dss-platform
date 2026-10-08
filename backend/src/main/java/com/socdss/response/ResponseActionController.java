package com.socdss.response;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/actions")
public class ResponseActionController {
    private final ResponseActionService actionService;

    public ResponseActionController(ResponseActionService actionService) {
        this.actionService = actionService;
    }

    @GetMapping
    public List<ResponseActionDto> list(@PathVariable Long incidentId) {
        return actionService.list(incidentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseActionDto create(@PathVariable Long incidentId,
                                    @Valid @RequestBody CreateResponseActionRequest request) {
        return actionService.create(incidentId, request);
    }

    @PostMapping("/{actionId}/approve")
    public ResponseActionDto approve(@PathVariable Long incidentId,
                                     @PathVariable String actionId,
                                     @Valid @RequestBody ActionDecisionRequest request) {
        return actionService.approve(incidentId, actionId, request);
    }

    @PostMapping("/{actionId}/reject")
    public ResponseActionDto reject(@PathVariable Long incidentId,
                                    @PathVariable String actionId,
                                    @Valid @RequestBody ActionDecisionRequest request) {
        return actionService.reject(incidentId, actionId, request);
    }
}
