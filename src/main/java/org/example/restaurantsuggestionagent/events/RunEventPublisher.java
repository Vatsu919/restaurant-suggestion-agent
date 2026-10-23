package org.example.restaurantsuggestionagent.events;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Records every event of every run and pushes it to subscribed browsers over SSE.
 *
 * <p>Events are kept in memory so that a client connecting late (or reconnecting)
 * first receives the full history and then the live tail. Persisting them to a
 * database is a later step; the public methods stay the same.
 */
@Component
public class RunEventPublisher {

    private static final long NO_TIMEOUT = 0L;

    private final Map<UUID, RunLog> runs = new ConcurrentHashMap<>();

    public void open(UUID runId) {
        runs.put(runId, new RunLog());
    }

    public AgentEvent publish(UUID runId, StepType step, EventStatus status,
                              String restaurantId, String message, Map<String, Object> data) {
        RunLog log = require(runId);
        synchronized (log) {
            AgentEvent event = new AgentEvent(runId, log.events.size() + 1, step, status,
                    restaurantId, message, copy(data), Instant.now());
            log.events.add(event);
            for (SseEmitter emitter : List.copyOf(log.subscribers)) {
                send(log, emitter, event);
            }
            return event;
        }
    }

    /** Replays the run's history to the new subscriber, then keeps it attached for live events. */
    public SseEmitter subscribe(UUID runId) {
        RunLog log = require(runId);
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        synchronized (log) {
            for (AgentEvent event : log.events) {
                send(log, emitter, event);
            }
            if (log.closed) {
                emitter.complete();
            } else {
                log.subscribers.add(emitter);
                emitter.onCompletion(() -> unsubscribe(log, emitter));
                emitter.onTimeout(() -> unsubscribe(log, emitter));
                emitter.onError(e -> unsubscribe(log, emitter));
            }
        }
        return emitter;
    }

    /** Marks the run finished and ends every open stream. */
    public void close(UUID runId) {
        RunLog log = require(runId);
        synchronized (log) {
            log.closed = true;
            log.subscribers.forEach(SseEmitter::complete);
            log.subscribers.clear();
        }
    }

    public List<AgentEvent> events(UUID runId) {
        RunLog log = require(runId);
        synchronized (log) {
            return List.copyOf(log.events);
        }
    }

    public boolean exists(UUID runId) {
        return runs.containsKey(runId);
    }

    /** Unlike Map.copyOf, keeps insertion order and allows null values (e.g. a missing website). */
    private static Map<String, Object> copy(Map<String, Object> data) {
        return data == null || data.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    private void send(RunLog log, SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(event.seq()))
                    .name("agent-event")
                    .data(event));
        } catch (IOException | IllegalStateException e) {
            // The browser went away; drop it so one dead tab can't stall the run.
            log.subscribers.remove(emitter);
        }
    }

    private void unsubscribe(RunLog log, SseEmitter emitter) {
        synchronized (log) {
            log.subscribers.remove(emitter);
        }
    }

    private RunLog require(UUID runId) {
        RunLog log = runs.get(runId);
        if (log == null) {
            throw new UnknownRunException(runId);
        }
        return log;
    }

    private static final class RunLog {
        private final List<AgentEvent> events = new ArrayList<>();
        private final List<SseEmitter> subscribers = new ArrayList<>();
        private boolean closed;
    }
}
