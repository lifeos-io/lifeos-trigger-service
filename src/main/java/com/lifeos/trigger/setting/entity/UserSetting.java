package com.lifeos.trigger.setting.entity;

import com.lifeos.trigger.common.persistence.AssignedIdEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The user's time zone, as Strategy last published it ({@code UserProfileUpdated}). */
@Entity
@Table(name = "user_setting")
public class UserSetting extends AssignedIdEntity {

    @Id
    private UUID userId;

    private String timezone;

    @Version
    private int version;

    private Instant updatedAt;

    protected UserSetting() {
    }

    public static UserSetting of(UUID userId, String timezone, Instant now) {
        UserSetting setting = new UserSetting();
        setting.userId = Objects.requireNonNull(userId);
        setting.timezone = Objects.requireNonNull(timezone);
        setting.updatedAt = now;
        return setting;
    }

    /** @return whether the time zone changed */
    public boolean changeTimezone(String timezone, Instant now) {
        if (timezone.equals(this.timezone)) {
            return false;
        }
        this.timezone = timezone;
        this.updatedAt = now;
        return true;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
