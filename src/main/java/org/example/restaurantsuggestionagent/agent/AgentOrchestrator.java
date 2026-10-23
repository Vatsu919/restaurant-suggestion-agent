package org.example.restaurantsuggestionagent.agent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.example.restaurantsuggestionagent.events.EventStatus;
import org.example.restaurantsuggestionagent.events.RunEventPublisher;
import org.example.restaurantsuggestionagent.events.StepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Runs the pipeline steps in a fixed order for each request. The order is
 * decided by code, not by the LLM, so every run is predictable and every
 * decision shows up in the event stream.
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final List<PipelineStep> steps;
    private final RunEventPublisher events;
    private final Executor executor;
    private final Map<UUID, RunContext> runs = new ConcurrentHashMap<>();

    @Autowired
    public AgentOrchestrator(ObjectProvider<PipelineStep> steps, RunEventPublisher events,
                             @Qualifier("agentExecutor") Executor executor) {
        this(steps.orderedStream().toList(), events, executor);
    }

    AgentOrchestrator(List<PipelineStep> steps, RunEventPublisher events, Executor executor) {
        this.steps = List.copyOf(steps);
        this.events = events;
        this.executor = executor;
    }

    /** Starts a run in the background and returns its id straight away. */
    public UUID start(String userMessage) {
        UUID runId = UUID.randomUUID();
        events.open(runId);
        RunContext context = new RunContext(runId, userMessage, events);
        runs.put(runId, context);
        executor.execute(() -> run(context));
        return runId;
    }

    public Optional<RunContext> find(UUID runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    private void run(RunContext context) {
        context.emit(StepType.RUN, EventStatus.STARTED, "Starting run",
                Map.of("steps", steps.stream().map(s -> s.type().name()).toList()));
        try {
            for (PipelineStep step : steps) {
                context.emit(step.type(), EventStatus.STARTED, step.description(), Map.of());
                try {
                    step.execute(context);
                } catch (Exception e) {
                    log.error("Run {} failed in step {}", context.runId(), step.type(), e);
                    context.emit(step.type(), EventStatus.FAILED, describe(e), Map.of());
                    context.status(RunStatus.FAILED);
                    context.emit(StepType.RUN, EventStatus.FAILED, "Run stopped at step " + step.type(), Map.of());
                    return;
                }
                context.emit(step.type(), EventStatus.COMPLETED, "Done", Map.of());
            }
            context.status(RunStatus.COMPLETED);
            context.emit(StepType.RUN, EventStatus.COMPLETED, "Run finished", Map.of());
        } finally {
            events.close(context.runId());
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
