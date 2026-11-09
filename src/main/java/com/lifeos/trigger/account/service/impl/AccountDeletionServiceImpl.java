package com.lifeos.trigger.account.service.impl;

import com.lifeos.trigger.account.service.AccountDeletionService;
import com.lifeos.trigger.job.repository.ScheduledJobRepository;
import com.lifeos.trigger.setting.repository.UserSettingRepository;
import com.lifeos.trigger.week.repository.WeekSnapshotRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes the user's jobs (pending ones included, so nothing is sent after the deletion), week numbers and time
 * zone. Their already published events age out of Kafka with the topic's retention. Repeating it is harmless.
 */
@Service
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionServiceImpl.class);

    private final ScheduledJobRepository scheduledJobRepository;
    private final WeekSnapshotRepository weekSnapshotRepository;
    private final UserSettingRepository userSettingRepository;

    public AccountDeletionServiceImpl(ScheduledJobRepository scheduledJobRepository,
            WeekSnapshotRepository weekSnapshotRepository, UserSettingRepository userSettingRepository) {
        this.scheduledJobRepository = scheduledJobRepository;
        this.weekSnapshotRepository = weekSnapshotRepository;
        this.userSettingRepository = userSettingRepository;
    }

    @Override
    @Transactional
    public void deleteAll(UUID userId) {
        int jobs = scheduledJobRepository.deleteByUserId(userId);
        int weeks = weekSnapshotRepository.deleteByUserId(userId);
        userSettingRepository.deleteById(userId);
        log.info("Deleted the data of user {}: {} jobs, {} weeks", userId, jobs, weeks);
    }
}
