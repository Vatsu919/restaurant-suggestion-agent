package org.example.restaurantsuggestionagent.agent;

import java.util.List;
import java.util.UUID;

import org.example.restaurantsuggestionagent.answer.FinalAnswer;
import org.example.restaurantsuggestionagent.events.AgentEvent;
import org.example.restaurantsuggestionagent.events.RunEventPublisher;
import org.example.restaurantsuggestionagent.events.UnknownRunException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * POST a request to start a run, then follow it on the SSE stream:
 * <pre>
 * POST /api/runs              {"message": "Vegetarian breakfast and dinner in Barcelona"}
 * GET  /api/runs/{id}/events  text/event-stream, full history then live events
 * GET  /api/runs/{id}         status, the final answer (once ready) and all events so far
 * </pre>
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final AgentOrchestrator orchestrator;
    private final RunEventPublisher events;

    public RunController(AgentOrchestrator orchestrator, RunEventPublisher events) {
        this.orchestrator = orchestrator;
        this.events = events;
    }

    public record StartRunRequest(String message) {
    }

    public record StartRunResponse(UUID runId, String eventsUrl) {
    }

    /** @param answer the final answer; null until the run has finished */
    public record RunView(UUID runId, RunStatus status, FinalAnswer answer, List<AgentEvent> events) {
    }

    @PostMapping
    public ResponseEntity<StartRunResponse> start(@RequestBody StartRunRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be empty");
        }
        UUID runId = orchestrator.start(request.message().strip());
        return ResponseEntity.accepted()
                .body(new StartRunResponse(runId, "/api/runs/" + runId + "/events"));
    }

    @GetMapping(path = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID runId) {
        return events.subscribe(runId);
    }

    @GetMapping("/{runId}")
    public RunView get(@PathVariable UUID runId) {
        RunContext context = orchestrator.find(runId).orElseThrow(() -> new UnknownRunException(runId));
        return new RunView(runId, context.status(), context.answer(), events.events(runId));
    }
}
