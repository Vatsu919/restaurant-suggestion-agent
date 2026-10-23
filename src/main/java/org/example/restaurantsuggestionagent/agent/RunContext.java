package org.example.restaurantsuggestionagent.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.example.restaurantsuggestionagent.answer.FinalAnswer;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.events.EventStatus;
import org.example.restaurantsuggestionagent.extraction.MenuReading;
import org.example.restaurantsuggestionagent.events.RunEventPublisher;
import org.example.restaurantsuggestionagent.events.StepType;
import org.example.restaurantsuggestionagent.menu.MenuSource;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.example.restaurantsuggestionagent.rank.RankedRestaurant;
import org.example.restaurantsuggestionagent.reviews.ReviewInsight;

/**
 * State shared by the pipeline steps of one run. Each step reads what earlier
 * steps produced and adds its own output; fields are added here as steps are built.
 */
public class RunContext {

    private final UUID runId;
    private final String userMessage;
    private final RunEventPublisher events;

    private volatile RunStatus status = RunStatus.RUNNING;
    private volatile TripQuery query;
    private volatile List<PlaceCandidate> candidates = List.of();
    private final List<SkippedRestaurant> skipped = new CopyOnWriteArrayList<>();
    private volatile Map<String, MenuSource> menus = Map.of();
    private volatile Map<String, MenuReading> menuReadings = Map.of();
    private volatile Map<String, ReviewInsight> reviewInsights = Map.of();
    private volatile List<RankedRestaurant> rankings = List.of();
    private volatile FinalAnswer answer;

    public RunContext(UUID runId, String userMessage, RunEventPublisher events) {
        this.runId = runId;
        this.userMessage = userMessage;
        this.events = events;
    }

    public UUID runId() {
        return runId;
    }

    public String userMessage() {
        return userMessage;
    }

    public RunStatus status() {
        return status;
    }

    void status(RunStatus status) {
        this.status = status;
    }

    /** The parsed request; set by the PARSE_QUERY step, read by every later step. */
    public TripQuery query() {
        return query;
    }

    public void query(TripQuery query) {
        this.query = query;
    }

    /** Restaurants still in the running; set by DISCOVER, narrowed by later steps. */
    public List<PlaceCandidate> candidates() {
        return candidates;
    }

    public void candidates(List<PlaceCandidate> candidates) {
        this.candidates = List.copyOf(candidates);
    }

    /** Every restaurant dropped so far, in the order it happened. */
    public List<SkippedRestaurant> skippedRestaurants() {
        return List.copyOf(skipped);
    }

    /**
     * Records that {@code place} is dropped and tells the user why. The caller is
     * responsible for leaving it out of {@link #candidates(List)}.
     */
    public void skip(StepType step, PlaceCandidate place, SkipReason reason, String detail) {
        skip(step, place, reason, detail, Map.of());
    }

    /** As {@link #skip(StepType, PlaceCandidate, SkipReason, String)}, with extra details for the UI. */
    public void skip(StepType step, PlaceCandidate place, SkipReason reason, String detail, Map<String, Object> extra) {
        skipped.add(new SkippedRestaurant(place.id(), place.name(), place.websiteUri(), place.googleMapsUri(),
                step, reason, detail));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reason", reason);
        data.put("name", place.name());
        data.put("googleMapsUri", place.googleMapsUri());
        data.put("websiteUri", place.websiteUri());
        data.putAll(extra);
        skipped(step, place.id(), place.name() + ": " + detail, data);
    }

    /** Downloaded menus by place id; set by RESOLVE_MENU. */
    public Map<String, MenuSource> menus() {
        return menus;
    }

    public void menus(Map<String, MenuSource> menus) {
        this.menus = Map.copyOf(menus);
    }

    /** Read menus (dishes, prices, matching dishes) by place id; set by EXTRACT_MENU. */
    public Map<String, MenuReading> menuReadings() {
        return menuReadings;
    }

    public void menuReadings(Map<String, MenuReading> menuReadings) {
        this.menuReadings = Map.copyOf(menuReadings);
    }

    /** What the reviews say, by place id; set by ANALYZE_REVIEWS. */
    public Map<String, ReviewInsight> reviewInsights() {
        return reviewInsights;
    }

    public void reviewInsights(Map<String, ReviewInsight> reviewInsights) {
        this.reviewInsights = Map.copyOf(reviewInsights);
    }

    /** All remaining restaurants, best first; set by RANK. */
    public List<RankedRestaurant> rankings() {
        return rankings;
    }

    public void rankings(List<RankedRestaurant> rankings) {
        this.rankings = List.copyOf(rankings);
    }

    /** The final answer; set by SUMMARIZE, null until then. */
    public FinalAnswer answer() {
        return answer;
    }

    public void answer(FinalAnswer answer) {
        this.answer = answer;
    }

    /** Reports what a step is doing right now, e.g. "Found menu at /carta (PDF, 3 pages)". */
    public void progress(StepType step, String message, Map<String, Object> data) {
        events.publish(runId, step, EventStatus.PROGRESS, null, message, data);
    }

    public void progress(StepType step, String restaurantId, String message, Map<String, Object> data) {
        events.publish(runId, step, EventStatus.PROGRESS, restaurantId, message, data);
    }

    /** Reports that a restaurant was dropped and why; it takes no further part in the run. */
    public void skipped(StepType step, String restaurantId, String message, Map<String, Object> data) {
        events.publish(runId, step, EventStatus.SKIPPED, restaurantId, message, data);
    }

    void emit(StepType step, EventStatus status, String message, Map<String, Object> data) {
        events.publish(runId, step, status, null, message, data);
    }
}
