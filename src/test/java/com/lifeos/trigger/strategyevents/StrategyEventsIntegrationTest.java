package com.lifeos.trigger.strategyevents;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lifeos.trigger.support.IntegrationTest;
import com.lifeos.trigger.support.StrategyPayloads;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What Trigger keeps and schedules from Strategy's events. Times: Istanbul is UTC+3, London UTC+0 in winter. */
class StrategyEventsIntegrationTest extends IntegrationTest {

    private static final LocalDate REVIEW_ON = LocalDate.parse("2026-12-15");
    private static final LocalDate FIRST_TRIGGER = LocalDate.parse("2026-12-01");
    private static final LocalDate LAST_TRIGGER = LocalDate.parse("2027-01-10");

    private StrategyPayloads payloads;

    @BeforeEach
    void payloads() {
        payloads = new StrategyPayloads(jsonMapper);
    }

    @Test
    void aDecisionSchedulesItsReviewAndItsDateTriggersOnlyOnce() {
        UUID user = UUID.randomUUID();
        UUID decision = UUID.randomUUID();

        strategyEvent(user, "DecisionRecorded",
                payloads.decisionRecorded(decision, "K8s experience", REVIEW_ON, FIRST_TRIGGER, LAST_TRIGGER));
        // Redelivered in a new event: nothing doubles.
        strategyEvent(user, "DecisionRecorded",
                payloads.decisionRecorded(decision, "K8s experience", REVIEW_ON, FIRST_TRIGGER, LAST_TRIGGER));

        // Default zone (no profile event yet): Europe/Istanbul, 09:00 local = 06:00 UTC. The EVENT trigger
        // (position 1) is not scheduled.
        assertEquals(List.of(
                        "REOPEN_TRIGGER/0 2026-12-01 09:00 2026-12-01T06:00:00Z PENDING",
                        "DECISION_REVIEW/0 2026-12-15 09:00 2026-12-15T06:00:00Z PENDING",
                        "REOPEN_TRIGGER/2 2027-01-10 09:00 2027-01-10T06:00:00Z PENDING"),
                jobs(user));
    }

    @Test
    void aClosedOrSupersededDecisionNeedsNoMoreReminders() {
        UUID user = UUID.randomUUID();
        UUID closed = UUID.randomUUID();
        UUID superseded = UUID.randomUUID();
        strategyEvent(user, "DecisionRecorded",
                payloads.decisionRecorded(closed, "Closed one", REVIEW_ON, FIRST_TRIGGER, LAST_TRIGGER));
        strategyEvent(user, "DecisionRecorded",
                payloads.decisionRecorded(superseded, "Superseded one", REVIEW_ON, FIRST_TRIGGER, LAST_TRIGGER));

        strategyEvent(user, "DecisionClosed", payloads.decisionClosed(closed));
        strategyEvent(user, "DecisionSuperseded", payloads.decisionSuperseded(superseded));

        assertEquals(6, jobs(user).size());
        assertEquals(List.of(), jobs(user).stream().filter(job -> !job.endsWith("CANCELLED")).toList());
    }

    @Test
    void movingToAnotherTimeZoneKeepsTheLocalTimes() {
        UUID user = UUID.randomUUID();
        UUID decision = UUID.randomUUID();
        strategyEvent(user, "DecisionRecorded",
                payloads.decisionRecorded(decision, "K8s experience", REVIEW_ON, FIRST_TRIGGER, LAST_TRIGGER));

        strategyEvent(user, "UserProfileUpdated", payloads.profile("Europe/London"));

        assertEquals(List.of(
                        "REOPEN_TRIGGER/0 2026-12-01 09:00 2026-12-01T09:00:00Z PENDING",
                        "DECISION_REVIEW/0 2026-12-15 09:00 2026-12-15T09:00:00Z PENDING",
                        "REOPEN_TRIGGER/2 2027-01-10 09:00 2027-01-10T09:00:00Z PENDING"),
                jobs(user));
        // New jobs use the stored zone right away.
        UUID next = UUID.randomUUID();
        strategyEvent(user, "DecisionRecorded",
                payloads.decisionRecorded(next, "Next", REVIEW_ON, FIRST_TRIGGER, LAST_TRIGGER));
        assertEquals(6, jobs(user).stream().filter(job -> job.contains("T09:00:00Z")).count());
    }

    @Test
    void aCommittedWeekIsTrackedUntilItIsReviewed() {
        UUID user = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        LocalDate monday = LocalDate.parse("2026-11-09");

        strategyEvent(user, "WeeklyPlanActivated", payloads.weekActivated(plan, "2026-W46", monday, 300, 4, 240));

        // Thursday and Sunday 18:00 in Istanbul.
        assertEquals(List.of(
                        "DRIFT_CHECK/0 2026-11-12 18:00 2026-11-12T15:00:00Z PENDING",
                        "WEEKLY_REVIEW/0 2026-11-15 18:00 2026-11-15T15:00:00Z PENDING"),
                jobs(user));
        assertEquals(Map.of("capacity_minutes", 300, "planned_count", 4, "done_count", 0, "planned_minutes", 240,
                "done_minutes", 0, "status", "ACTIVE"), week(plan));

        strategyEvent(user, "ActionStatusChanged",
                payloads.actionStatusChanged(plan, "2026-W46", 3, 1, 0, 0, 240, 60));
        strategyEvent(user, "CapacityChanged", payloads.capacityChanged(plan, "2026-W46", 300, 270));
        assertEquals(Map.of("capacity_minutes", 270, "planned_count", 3, "done_count", 1, "planned_minutes", 240,
                "done_minutes", 60, "status", "ACTIVE"), week(plan));

        strategyEvent(user, "WeeklyReviewCompleted", payloads.weeklyReviewCompleted(plan, "2026-W46", monday));
        assertEquals("CLOSED", week(plan).get("status"));
        assertEquals(List.of(
                        "DRIFT_CHECK/0 2026-11-12 18:00 2026-11-12T15:00:00Z CANCELLED",
                        "WEEKLY_REVIEW/0 2026-11-15 18:00 2026-11-15T15:00:00Z CANCELLED"),
                jobs(user));
    }

    @Test
    void numbersOfAWeekTriggerNeverSawStartAreIgnored() {
        UUID user = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();

        strategyEvent(user, "ActionStatusChanged",
                payloads.actionStatusChanged(unknown, "2026-W46", 1, 0, 0, 0, 60, 0));
        strategyEvent(user, "CapacityChanged", payloads.capacityChanged(unknown, "2026-W46", 300, 200));

        assertEquals(0L, jdbcClient.sql("SELECT count(*) FROM week_snapshot WHERE weekly_plan_id = :id")
                .param("id", unknown).query(Long.class).single());
    }

    @Test
    void aDeletedUserLeavesNothingBehind() {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        for (UUID someone : List.of(user, other)) {
            strategyEvent(someone, "UserProfileUpdated", payloads.profile("Europe/London"));
            strategyEvent(someone, "DecisionRecorded",
                    payloads.decisionRecorded(UUID.randomUUID(), "D", REVIEW_ON, FIRST_TRIGGER, LAST_TRIGGER));
            strategyEvent(someone, "WeeklyPlanActivated",
                    payloads.weekActivated(UUID.randomUUID(), "2026-W46", LocalDate.parse("2026-11-09"), 300, 1, 60));
        }

        strategyEvent(user, "UserDeletionRequested", payloads.userDeletionRequested());
        strategyEvent(user, "UserDeletionRequested", payloads.userDeletionRequested());

        assertEquals(Map.of("user_setting", 0L, "scheduled_job", 0L, "week_snapshot", 0L), rows(user));
        assertEquals(Map.of("user_setting", 1L, "scheduled_job", 5L, "week_snapshot", 1L), rows(other));
    }

    /** The user's jobs as "KIND/subKey dueOn dueTime dueAt STATUS", by due time. */
    private List<String> jobs(UUID user) {
        return jdbcClient.sql("""
                        SELECT kind || '/' || sub_key || ' ' || due_on || ' ' || to_char(due_time, 'HH24:MI') || ' '
                               || to_char(due_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') || ' ' || status
                        FROM scheduled_job WHERE user_id = :userId ORDER BY due_at, kind, sub_key
                        """)
                .param("userId", user).query(String.class).list();
    }

    private Map<String, Object> week(UUID plan) {
        return jdbcClient.sql("""
                        SELECT capacity_minutes, planned_count, done_count, planned_minutes, done_minutes, status
                        FROM week_snapshot WHERE weekly_plan_id = :id
                        """)
                .param("id", plan).query().singleRow();
    }

    private Map<String, Long> rows(UUID user) {
        return Map.of(
                "user_setting", count("SELECT count(*) FROM user_setting WHERE user_id = :u", user),
                "scheduled_job", count("SELECT count(*) FROM scheduled_job WHERE user_id = :u", user),
                "week_snapshot", count("SELECT count(*) FROM week_snapshot WHERE user_id = :u", user));
    }

    private long count(String sql, UUID user) {
        return jdbcClient.sql(sql).param("u", user).query(Long.class).single();
    }
}
