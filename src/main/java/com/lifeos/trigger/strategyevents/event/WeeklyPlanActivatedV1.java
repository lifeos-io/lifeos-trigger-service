package com.lifeos.trigger.strategyevents.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WeeklyPlanActivatedV1(UUID weeklyPlanId, String isoWeek, LocalDate weekStart, int capacityMinutes,
        int plannedActionCount, int plannedActionMinutes) {

    public static final String TYPE = "WeeklyPlanActivated";
}
