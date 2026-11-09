package com.lifeos.trigger.support;

import java.time.LocalDate;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Complete, schema-valid payloads of the Strategy events Trigger consumes (the base test checks each one against
 * lifeos-contracts), so the tests feed Trigger what Strategy really publishes.
 */
public final class StrategyPayloads {

    private final JsonMapper jsonMapper;

    public StrategyPayloads(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public JsonNode profile(String timezone) {
        return json("""
                {"displayName": "Onurcan", "timezone": "%s", "locale": "tr-TR", "defaultWeeklyMinutes": 300,
                 "onboardingCompletedAt": null}
                """.formatted(timezone));
    }

    /** A decision with a DATE trigger on {@code firstTrigger}, an EVENT trigger, and a DATE trigger on {@code lastTrigger}. */
    public JsonNode decisionRecorded(UUID decisionId, String title, LocalDate reviewOn, LocalDate firstTrigger,
            LocalDate lastTrigger) {
        return json("""
                {"decisionId": "%s", "goalId": "%s", "title": "%s", "context": null,
                 "alternatives": [
                   {"label": "A", "description": "Transfer", "chosen": false, "rejectionReason": "Not in my hands"},
                   {"label": "B", "description": "Own k3s server", "chosen": true, "rejectionReason": null}],
                 "expectedOutcome": "K8s on my CV", "decidedAt": "2026-11-01T10:00:00Z", "reviewOn": "%s",
                 "assumptionIds": [],
                 "reopenTriggers": [
                   {"kind": "DATE", "description": "First check", "eventType": null, "firesOn": "%s"},
                   {"kind": "EVENT", "description": "If I get the K8s project", "eventType": null, "firesOn": null},
                   {"kind": "DATE", "description": "Second check", "eventType": null, "firesOn": "%s"}]}
                """.formatted(decisionId, UUID.randomUUID(), title, reviewOn, firstTrigger, lastTrigger));
    }

    public JsonNode decisionClosed(UUID decisionId) {
        return json("""
                {"decisionId": "%s", "goalId": null, "title": "Closed", "expectedOutcome": "x", "outcomeNote": "y",
                 "wouldDecideAgain": "YES", "decidedAt": "2026-11-01T10:00:00Z", "closedAt": "2026-11-09T10:00:00Z"}
                """.formatted(decisionId));
    }

    public JsonNode decisionSuperseded(UUID decisionId) {
        return json("""
                {"decisionId": "%s", "supersededById": "%s", "supersededAt": "2026-11-09T10:00:00Z"}
                """.formatted(decisionId, UUID.randomUUID()));
    }

    public JsonNode weekActivated(UUID weeklyPlanId, String isoWeek, LocalDate weekStart, int capacity,
            int plannedCount, int plannedMinutes) {
        return json("""
                {"weeklyPlanId": "%s", "isoWeek": "%s", "weekStart": "%s", "capacityMinutes": %d, "headline": null,
                 "allocations": [{"goalId": "%s", "strategyVersionId": null, "allocatedMinutes": %d}],
                 "plannedActionCount": %d, "plannedActionMinutes": %d}
                """.formatted(weeklyPlanId, isoWeek, weekStart, capacity, UUID.randomUUID(), capacity, plannedCount,
                plannedMinutes));
    }

    public JsonNode capacityChanged(UUID weeklyPlanId, String isoWeek, int previous, int capacity) {
        return json("""
                {"weeklyPlanId": "%s", "isoWeek": "%s", "previousCapacityMinutes": %d, "capacityMinutes": %d}
                """.formatted(weeklyPlanId, isoWeek, previous, capacity));
    }

    /** An action became DONE; {@code week} totals as given. */
    public JsonNode actionStatusChanged(UUID weeklyPlanId, String isoWeek, int planned, int done, int deferred,
            int dropped, int plannedMinutes, int doneMinutes) {
        return json("""
                {"actionId": "%s", "weeklyPlanId": "%s", "isoWeek": "%s", "goalIds": ["%s"], "phaseKey": null,
                 "kind": "TECHNICAL", "estimateMinutes": 60, "previousStatus": "PLANNED", "status": "DONE",
                 "deferredCount": 0, "completedAt": "2026-11-10T10:00:00Z",
                 "week": {"planned": %d, "done": %d, "deferred": %d, "dropped": %d,
                          "plannedMinutes": %d, "doneMinutes": %d}}
                """.formatted(UUID.randomUUID(), weeklyPlanId, isoWeek, UUID.randomUUID(), planned, done, deferred,
                dropped, plannedMinutes, doneMinutes));
    }

    public JsonNode weeklyReviewCompleted(UUID weeklyPlanId, String isoWeek, LocalDate weekStart) {
        return json("""
                {"weeklyReviewId": "%s", "weeklyPlanId": "%s", "isoWeek": "%s", "weekStart": "%s",
                 "capacityMinutes": 300,
                 "actions": {"planned": 0, "done": 1, "deferred": 0, "dropped": 0, "plannedMinutes": 60,
                             "doneMinutes": 60},
                 "goals": [], "evidenceCount": 0, "energy": 4, "wentWell": null, "gotStuck": null, "lesson": null,
                 "nextWeekFocus": null, "completedAt": "2026-11-15T10:00:00Z"}
                """.formatted(UUID.randomUUID(), weeklyPlanId, isoWeek, weekStart));
    }

    public JsonNode userDeletionRequested() {
        return json("{\"requestedAt\": \"2026-11-09T10:00:00Z\"}");
    }

    private JsonNode json(String text) {
        return jsonMapper.readTree(text);
    }
}
