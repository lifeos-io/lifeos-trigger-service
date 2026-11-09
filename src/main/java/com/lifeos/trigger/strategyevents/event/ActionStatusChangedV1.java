package com.lifeos.trigger.strategyevents.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** Trigger reads only the week's totals after the change. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionStatusChangedV1(UUID weeklyPlanId, WeekV1 week) {

    public static final String TYPE = "ActionStatusChanged";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeekV1(int planned, int done, int deferred, int dropped, int plannedMinutes, int doneMinutes) {
    }
}
