package com.lifeos.trigger.week.service;

import java.time.LocalDate;
import java.util.UUID;

/** The active week's numbers, kept for the drift check, and the week's jobs. */
public interface WeekSnapshotService {

    /** A week was committed: keep its numbers and schedule its review reminder and drift check. */
    void activated(UUID userId, UUID weeklyPlanId, String isoWeek, LocalDate weekStart, int capacityMinutes,
            int plannedCount, int plannedMinutes);

    void capacityChanged(UUID weeklyPlanId, int capacityMinutes);

    void totalsChanged(UUID weeklyPlanId, int planned, int done, int deferred, int dropped, int plannedMinutes,
            int doneMinutes);

    /** The week was reviewed: it no longer needs a reminder or a drift check. */
    void reviewed(UUID weeklyPlanId);
}
