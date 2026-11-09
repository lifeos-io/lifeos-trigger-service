package com.lifeos.trigger.job;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifeos.trigger.job.service.JobRunService;
import com.lifeos.trigger.support.EventSchemas;
import com.lifeos.trigger.support.IntegrationTest;
import com.lifeos.trigger.support.StrategyPayloads;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Due jobs, run at the test clock's time: their events, the drift rule, and running each job once. */
class JobRunIntegrationTest extends IntegrationTest {

    private static final LocalDate MONDAY = LocalDate.parse("2026-11-09");

    @Autowired
    private JobRunService jobRunService;

    private StrategyPayloads payloads;

    @BeforeEach
    void startOfTheWeek() {
        payloads = new StrategyPayloads(jsonMapper);
        clock.set(Instant.parse("2026-11-09T07:00:00Z"));
        drain();
    }

    @Test
    void dueJobsPublishTheirEventsOnceAndOnTime() {
        UUID user = UUID.randomUUID();
        UUID decision = UUID.randomUUID();
        strategyEvent(user, "DecisionRecorded", payloads.decisionRecorded(decision, "K8s experience",
                LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-12"), LocalDate.parse("2026-12-10")));

        // Thursday 05:59 UTC: nothing is due yet (09:00 Istanbul = 06:00 UTC).
        clock.set(Instant.parse("2026-11-12T05:59:00Z"));
        drain();
        assertEquals(List.of(), outboxPayloads(user, "ReopenTriggerFired"));

        clock.set(Instant.parse("2026-11-12T06:00:00Z"));
        drain();
        JsonNode fired = outboxPayloads(user, "ReopenTriggerFired").getFirst();
        EventSchemas.assertValidPayload("ReopenTriggerFired", 1, fired);
        assertEquals(decision.toString(), fired.get("decisionId").asString());
        assertEquals(0, fired.get("position").asInt());
        assertEquals("2026-11-12T06:00:00Z", fired.get("dueAt").asString());

        // Trigger was down for a week: the review runs late, with the moment it was meant for.
        clock.set(Instant.parse("2026-11-27T12:00:00Z"));
        drain();
        drain();
        List<JsonNode> reviews = outboxPayloads(user, "DecisionReviewDue");
        assertEquals(1, reviews.size());
        EventSchemas.assertValidPayload("DecisionReviewDue", 1, reviews.getFirst());
        assertEquals("K8s experience", reviews.getFirst().get("title").asString());
        assertEquals("2026-11-20T06:00:00Z", reviews.getFirst().get("dueAt").asString());
        assertEquals(1, outboxPayloads(user, "ReopenTriggerFired").size());
    }

    @Test
    void aWeekBehindPaceIsReportedAtTheMidWeekCheck() {
        UUID behind = UUID.randomUUID();
        UUID onPace = UUID.randomUUID();
        UUID behindPlan = week(behind, 300, 240);
        UUID onPacePlan = week(onPace, 300, 240);
        // Behind: 100 of 240 minutes done (threshold 120). On pace: exactly 120.
        strategyEvent(behind, "ActionStatusChanged",
                payloads.actionStatusChanged(behindPlan, "2026-W46", 2, 1, 1, 0, 240, 100));
        strategyEvent(onPace, "ActionStatusChanged",
                payloads.actionStatusChanged(onPacePlan, "2026-W46", 2, 2, 0, 0, 240, 120));

        clock.set(Instant.parse("2026-11-12T15:00:00Z"));
        drain();

        JsonNode drift = outboxPayloads(behind, "DriftDetected").getFirst();
        EventSchemas.assertValidPayload("DriftDetected", 1, drift);
        assertEquals("BEHIND_PACE", drift.get("kind").asString());
        assertEquals(240, drift.get("plannedMinutes").asInt());
        assertEquals(100, drift.get("doneMinutes").asInt());
        assertEquals(120, drift.get("thresholdMinutes").asInt());
        assertEquals(1, drift.get("deferredActions").asInt());
        assertEquals(List.of(), outboxPayloads(onPace, "DriftDetected"));
        // Both checks ran.
        assertEquals(List.of("FIRED"), statuses(onPacePlan, "DRIFT_CHECK"));
    }

    @Test
    void theWeeklyReviewReminderGoesOnlyToAWeekStillRunning() {
        UUID user = UUID.randomUUID();
        UUID plan = week(user, 300, 0);
        UUID reviewedEarly = week(UUID.randomUUID(), 300, 0);
        // Reviewed (closed) without its jobs being cancelled: the run itself must not remind.
        jdbcClient.sql("UPDATE week_snapshot SET status = 'CLOSED' WHERE weekly_plan_id = :id")
                .param("id", reviewedEarly).update();

        clock.set(Instant.parse("2026-11-15T15:00:00Z"));
        drain();

        JsonNode reminder = outboxPayloads(user, "WeeklyReviewDue").getFirst();
        EventSchemas.assertValidPayload("WeeklyReviewDue", 1, reminder);
        assertEquals("2026-W46", reminder.get("isoWeek").asString());
        assertEquals(0L, jdbcClient.sql("""
                        SELECT count(*) FROM outbox_event
                        WHERE event_type = 'WeeklyReviewDue' AND payload->>'weeklyPlanId' = :id
                        """)
                .param("id", reviewedEarly.toString()).query(Long.class).single());
        assertEquals(List.of("FIRED"), statuses(reviewedEarly, "WEEKLY_REVIEW"));
        // Nothing planned: no drift either way.
        assertEquals(List.of(), outboxPayloads(user, "DriftDetected"));
    }

    @Test
    void concurrentRunnersRunEveryJobExactlyOnce() throws Exception {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < 60; i++) {
            strategyEvent(user, "DecisionRecorded", payloads.decisionRecorded(UUID.randomUUID(), "D" + i,
                    LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-10")));
        }
        clock.set(Instant.parse("2026-11-10T06:00:00Z"));

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> runners = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            runners.add(() -> {
                start.await();
                int total = 0;
                int ran;
                do {
                    ran = jobRunService.runDue(7);
                    total += ran;
                } while (ran > 0);
                return total;
            });
        }
        int total = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            List<Future<Integer>> futures = runners.stream().map(executor::submit).toList();
            start.countDown();
            for (Future<Integer> future : futures) {
                total += future.get();
            }
        }

        // 60 reviews + 120 date triggers, each published once (other tests' due jobs may have run too).
        assertTrue(total >= 180, "ran " + total);
        assertEquals(60, outboxPayloads(user, "DecisionReviewDue").size());
        assertEquals(120, outboxPayloads(user, "ReopenTriggerFired").size());
    }

    @Test
    void upcomingListsTheUsersRemindersSoonestFirst() throws Exception {
        UUID user = UUID.randomUUID();
        UUID decision = UUID.randomUUID();
        strategyEvent(user, "DecisionRecorded", payloads.decisionRecorded(decision, "K8s experience",
                LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-12"), LocalDate.parse("2026-12-10")));
        week(user, 300, 240);

        mockMvc.perform(get("/api/triggers/upcoming").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].kind").value(contains(
                        "REOPEN_TRIGGER", "WEEKLY_REVIEW", "DECISION_REVIEW", "REOPEN_TRIGGER")))
                .andExpect(jsonPath("$[*].label").value(contains(
                        "First check", "2026-W46", "K8s experience", "Second check")))
                .andExpect(jsonPath("$[0].dueOn").value("2026-11-12"))
                .andExpect(jsonPath("$[0].dueTime").value("09:00:00"))
                .andExpect(jsonPath("$[0].subjectId").value(decision.toString()));

        clock.set(Instant.parse("2026-11-12T06:00:00Z"));
        drain();
        mockMvc.perform(get("/api/triggers/upcoming").with(as(user)))
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/triggers/upcoming").with(as(UUID.randomUUID())))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/triggers/upcoming")).andExpect(status().isUnauthorized());
    }

    /** A committed week 2026-W46 with {@code plannedMinutes} of planned work; returns its plan ID. */
    private UUID week(UUID user, int capacity, int plannedMinutes) {
        UUID plan = UUID.randomUUID();
        strategyEvent(user, "WeeklyPlanActivated",
                payloads.weekActivated(plan, "2026-W46", MONDAY, capacity, plannedMinutes == 0 ? 0 : 2,
                        plannedMinutes));
        return plan;
    }

    private List<String> statuses(UUID subject, String kind) {
        return jdbcClient.sql("SELECT status FROM scheduled_job WHERE subject_id = :id AND kind = :kind")
                .param("id", subject).param("kind", kind).query(String.class).list();
    }

    /** Runs everything due at the clock's time (jobs of earlier tests included, so each test starts clean). */
    private void drain() {
        int ran;
        do {
            ran = jobRunService.runDue(100);
        } while (ran > 0);
        assertTrue(ran >= 0);
    }
}
