package org.example.restaurantsuggestionagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.example.restaurantsuggestionagent.events.AgentEvent;
import org.example.restaurantsuggestionagent.events.EventStatus;
import org.example.restaurantsuggestionagent.events.RunEventPublisher;
import org.example.restaurantsuggestionagent.events.StepType;
import org.junit.jupiter.api.Test;

class AgentOrchestratorTest {

    private final RunEventPublisher events = new RunEventPublisher();

    /** Runs the pipeline on the calling thread so the test can assert right after start(). */
    private AgentOrchestrator orchestrator(PipelineStep... steps) {
        return new AgentOrchestrator(List.of(steps), events, Runnable::run);
    }

    @Test
    void runsStepsInOrderAndRecordsEveryEvent() {
        PipelineStep discover = step(StepType.DISCOVER, ctx ->
                ctx.progress(StepType.DISCOVER, "Found 20 restaurants", Map.of("count", 20)));
        PipelineStep filter = step(StepType.FILTER, ctx ->
                ctx.skipped(StepType.FILTER, "place-1", "No website", Map.of("reason", "NO_WEBSITE")));

        AgentOrchestrator orchestrator = orchestrator(discover, filter);
        UUID runId = orchestrator.start("veg breakfast in Barcelona");

        assertThat(orchestrator.find(runId)).get().extracting(RunContext::status).isEqualTo(RunStatus.COMPLETED);
        assertThat(events.events(runId))
                .extracting(e -> e.step() + ":" + e.status())
                .containsExactly(
                        "RUN:STARTED",
                        "DISCOVER:STARTED", "DISCOVER:PROGRESS", "DISCOVER:COMPLETED",
                        "FILTER:STARTED", "FILTER:SKIPPED", "FILTER:COMPLETED",
                        "RUN:COMPLETED");
        assertThat(events.events(runId)).extracting(AgentEvent::seq).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void failingStepStopsTheRunAndLaterStepsDoNotRun() {
        PipelineStep failing = step(StepType.DISCOVER, ctx -> {
            throw new IllegalStateException("Places API returned 403");
        });
        PipelineStep never = step(StepType.FILTER, ctx -> {
            throw new AssertionError("must not run");
        });

        AgentOrchestrator orchestrator = orchestrator(failing, never);
        UUID runId = orchestrator.start("anything");

        assertThat(orchestrator.find(runId)).get().extracting(RunContext::status).isEqualTo(RunStatus.FAILED);
        List<AgentEvent> recorded = events.events(runId);
        assertThat(recorded).extracting(e -> e.step() + ":" + e.status())
                .containsExactly("RUN:STARTED", "DISCOVER:STARTED", "DISCOVER:FAILED", "RUN:FAILED");
        assertThat(recorded.get(2).message()).isEqualTo("Places API returned 403");
    }

    @Test
    void lateSubscriberOnFinishedRunGetsCompletedStream() {
        AgentOrchestrator orchestrator = orchestrator();
        UUID runId = orchestrator.start("anything");

        // Run is already closed; subscribing must replay and complete rather than hang.
        assertThat(events.subscribe(runId)).isNotNull();
        assertThat(events.events(runId)).extracting(AgentEvent::status)
                .containsExactly(EventStatus.STARTED, EventStatus.COMPLETED);
    }

    private interface Body {
        void run(RunContext context) throws Exception;
    }

    private static PipelineStep step(StepType type, Body body) {
        return new PipelineStep() {
            @Override
            public StepType type() {
                return type;
            }

            @Override
            public String description() {
                return "Test step " + type;
            }

            @Override
            public void execute(RunContext context) throws Exception {
                body.run(context);
            }
        };
    }
}
