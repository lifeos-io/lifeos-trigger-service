package com.lifeos.trigger.setting.service;

import java.time.ZoneId;
import java.util.UUID;

/** The user's time zone, from Strategy's {@code UserProfileUpdated}. */
public interface UserSettingService {

    /** Strategy's default for a profile it just created; used until the user's profile event arrived. */
    String DEFAULT_TIMEZONE = "Europe/Istanbul";

    ZoneId zoneOf(UUID userId);

    /** @return whether the time zone changed (the user's pending jobs must move) */
    boolean timezoneChanged(UUID userId, String timezone);
}
