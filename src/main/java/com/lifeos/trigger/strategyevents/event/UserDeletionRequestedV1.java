package com.lifeos.trigger.strategyevents.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The user is the envelope's userId; the payload has nothing Trigger needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDeletionRequestedV1() {

    public static final String TYPE = "UserDeletionRequested";
}
