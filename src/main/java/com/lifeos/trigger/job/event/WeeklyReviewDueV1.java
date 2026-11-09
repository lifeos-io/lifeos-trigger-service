package com.lifeos.trigger.job.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Payload of {@code WeeklyReviewDue} v1 (lifeos-contracts: schemas/trigger/WeeklyReviewDue.v1.schema.json). */
public record WeeklyReviewDueV1(UUID weeklyPlanId, String isoWeek, LocalDate weekStart, Instant dueAt) {

    public static final String TYPE = "WeeklyReviewDue";
    public static final int VERSION = 1;
}
