package com.lifeos.trigger.strategyevents.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DecisionRecordedV1(UUID decisionId, UUID goalId, String title, LocalDate reviewOn,
        List<TriggerV1> reopenTriggers) {

    public static final String TYPE = "DecisionRecorded";

    /** {@code kind} DATE has {@code firesOn}; EVENT triggers are things in the user's life Trigger cannot see. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TriggerV1(String kind, String description, LocalDate firesOn) {
    }
}
