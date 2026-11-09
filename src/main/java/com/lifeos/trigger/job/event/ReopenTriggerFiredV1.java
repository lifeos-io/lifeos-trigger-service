package com.lifeos.trigger.job.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Payload of {@code ReopenTriggerFired} v1 (lifeos-contracts: schemas/trigger/ReopenTriggerFired.v1.schema.json). */
public record ReopenTriggerFiredV1(UUID decisionId, int position, String description, LocalDate firesOn,
        Instant dueAt) {

    public static final String TYPE = "ReopenTriggerFired";
    public static final int VERSION = 1;
}
