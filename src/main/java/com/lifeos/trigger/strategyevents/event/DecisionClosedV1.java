package com.lifeos.trigger.strategyevents.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DecisionClosedV1(UUID decisionId) {

    public static final String TYPE = "DecisionClosed";
}
