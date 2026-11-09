package com.lifeos.trigger.job.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Payload of {@code DecisionReviewDue} v1 (lifeos-contracts: schemas/trigger/DecisionReviewDue.v1.schema.json). */
public record DecisionReviewDueV1(UUID decisionId, UUID goalId, String title, LocalDate reviewOn, Instant dueAt) {

    public static final String TYPE = "DecisionReviewDue";
    public static final int VERSION = 1;
}
