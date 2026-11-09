package com.lifeos.trigger.strategyevents.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WeeklyReviewCompletedV1(UUID weeklyPlanId) {

    public static final String TYPE = "WeeklyReviewCompleted";
}
