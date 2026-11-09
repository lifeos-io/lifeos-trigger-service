package com.lifeos.trigger.strategyevents.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfileUpdatedV1(String timezone) {

    public static final String TYPE = "UserProfileUpdated";
}
