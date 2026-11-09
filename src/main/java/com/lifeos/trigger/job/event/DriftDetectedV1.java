package com.lifeos.trigger.job.event;

import java.time.Instant;
import java.util.UUID;

/** Payload of {@code DriftDetected} v1 (lifeos-contracts: schemas/trigger/DriftDetected.v1.schema.json). */
public record DriftDetectedV1(UUID weeklyPlanId, String isoWeek, Kind kind, int capacityMinutes, int plannedMinutes,
        int doneMinutes, int thresholdMinutes, int deferredActions, Instant checkedAt) {

    public static final String TYPE = "DriftDetected";
    public static final int VERSION = 1;

    public enum Kind {
        /** Done work below a share of the planned work at the mid-week check. */
        BEHIND_PACE
    }
}
