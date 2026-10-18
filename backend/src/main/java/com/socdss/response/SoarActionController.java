package com.socdss.response;

import com.socdss.auth.AuthSession;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/response-actions")
public class SoarActionController {
    private final ResponseActionService actionService;
    private final BulkResponseActionService bulkActionService;

    public SoarActionController(ResponseActionService actionService,
                                BulkResponseActionService bulkActionService) {
        this.actionService = actionService;
        this.bulkActionService = bulkActionService;
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

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String actionType,
                                            @RequestParam(required = false) String search) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=soar-action-history.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(actionService.exportCsv(status, actionType, search));
    }

    @PostMapping("/bulk-dispatch")
    public BulkDispatchResultDto bulkDispatch(@Valid @RequestBody BulkDispatchRequest request,
                                              HttpSession session) {
        Object username = session.getAttribute(AuthSession.USERNAME);
        if (!(username instanceof String actor) || actor.isBlank()) {
            throw new IllegalArgumentException("Authenticated username is required");
        }
        return bulkActionService.dispatch(request, actor);
    }
}
