package com.lifeos.trigger.strategyevents.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CapacityChangedV1(UUID weeklyPlanId, int capacityMinutes) {

    public static final String TYPE = "CapacityChanged";
}
