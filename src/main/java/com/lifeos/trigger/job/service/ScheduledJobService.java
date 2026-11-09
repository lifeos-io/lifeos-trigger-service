package com.lifeos.trigger.job.service;

import com.lifeos.trigger.job.dto.UpcomingJobResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Trigger's schedule. Scheduling is idempotent (a job is identified by kind, subject and sub-key), so a redelivered
 * event changes nothing; times are the user's local times, turned into instants with their time zone.
 */
public interface ScheduledJobService {

    /** {@code DecisionReviewDue} on the morning of {@code reviewOn}. */
    void scheduleDecisionReview(UUID userId, UUID decisionId, UUID goalId, String title, LocalDate reviewOn);

    /** {@code ReopenTriggerFired} on the morning of {@code firesOn}; {@code position} is the trigger's index. */
    void scheduleReopenTrigger(UUID userId, UUID decisionId, int position, String description, LocalDate firesOn);

    /** The week's Sunday-evening review reminder and Thursday-evening drift check. */
    void scheduleWeek(UUID userId, UUID weeklyPlanId, String isoWeek, LocalDate weekStart);

    /** Cancels the pending jobs about a decision or a weekly plan. */
    void cancelFor(UUID subjectId);

    /** The user's time zone changed: their pending jobs keep their local times in the new zone. */
    void moveTo(UUID userId, ZoneId zone);

    /** What the user will be told about, soonest first (the drift check is internal and not listed). */
    List<UpcomingJobResponse> upcoming(UUID userId);
}
