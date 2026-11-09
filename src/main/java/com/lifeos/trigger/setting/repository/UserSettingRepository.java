package com.lifeos.trigger.setting.repository;

import com.lifeos.trigger.setting.entity.UserSetting;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingRepository extends JpaRepository<UserSetting, UUID> {
}
