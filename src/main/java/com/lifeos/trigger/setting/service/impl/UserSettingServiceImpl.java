package com.lifeos.trigger.setting.service.impl;

import com.lifeos.trigger.setting.entity.UserSetting;
import com.lifeos.trigger.setting.repository.UserSettingRepository;
import com.lifeos.trigger.setting.service.UserSettingService;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSettingServiceImpl implements UserSettingService {

    private final UserSettingRepository repository;
    private final Clock clock;

    public UserSettingServiceImpl(UserSettingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID userId) {
        return ZoneId.of(repository.findById(userId).map(UserSetting::getTimezone).orElse(DEFAULT_TIMEZONE));
    }

    @Override
    @Transactional
    public boolean timezoneChanged(UUID userId, String timezone) {
        ZoneId.of(timezone); // Strategy validated it; a bad one fails here before anything is stored
        return repository.findById(userId)
                .map(setting -> setting.changeTimezone(timezone, clock.instant()))
                .orElseGet(() -> {
                    repository.save(UserSetting.of(userId, timezone, clock.instant()));
                    return !timezone.equals(DEFAULT_TIMEZONE);
                });
    }
}
