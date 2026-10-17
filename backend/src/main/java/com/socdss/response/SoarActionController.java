package com.socdss.response;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/response-actions")
public class SoarActionController {
    private final ResponseActionService actionService;

    public SoarActionController(ResponseActionService actionService) {
        this.actionService = actionService;
    }

    @GetMapping
    public ResponseActionPageDto page(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "25") int size,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String actionType,
                                      @RequestParam(required = false) String search) {
        return actionService.page(page, size, status, actionType, search);
    }

    @GetMapping("/summary")
    public ResponseActionSummaryDto summary() {
        return actionService.summary();
    }
}
