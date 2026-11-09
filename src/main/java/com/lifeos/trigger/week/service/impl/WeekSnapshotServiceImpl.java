package com.lifeos.trigger.week.service.impl;

import com.lifeos.trigger.job.service.ScheduledJobService;
import com.lifeos.trigger.week.entity.WeekSnapshot;
import com.lifeos.trigger.week.repository.WeekSnapshotRepository;
import com.lifeos.trigger.week.service.WeekSnapshotService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One user's events arrive in order, so the snapshot follows Strategy's week. A week Trigger never saw activated
 * (e.g. activated before Trigger was running) is ignored: there is nothing to check it against.
 */
@Service
public class WeekSnapshotServiceImpl implements WeekSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(WeekSnapshotServiceImpl.class);

    private final WeekSnapshotRepository repository;
    private final ScheduledJobService scheduledJobService;
    private final Clock clock;

    public WeekSnapshotServiceImpl(WeekSnapshotRepository repository, ScheduledJobService scheduledJobService,
            Clock clock) {
        this.repository = repository;
        this.scheduledJobService = scheduledJobService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void activated(UUID userId, UUID weeklyPlanId, String isoWeek, LocalDate weekStart, int capacityMinutes,
            int plannedCount, int plannedMinutes) {
        if (repository.findById(weeklyPlanId).isEmpty()) {
            repository.save(WeekSnapshot.activated(weeklyPlanId, userId, isoWeek, weekStart, capacityMinutes,
                    plannedCount, plannedMinutes, clock.instant()));
        }
        scheduledJobService.scheduleWeek(userId, weeklyPlanId, isoWeek, weekStart);
    }

    @Override
    @Transactional
    public void capacityChanged(UUID weeklyPlanId, int capacityMinutes) {
        repository.findById(weeklyPlanId).ifPresentOrElse(
                week -> week.changeCapacity(capacityMinutes, clock.instant()),
                () -> log.info("Ignoring capacity change of unknown week {}", weeklyPlanId));
    }

    @Override
    @Transactional
    public void totalsChanged(UUID weeklyPlanId, int planned, int done, int deferred, int dropped,
            int plannedMinutes, int doneMinutes) {
        repository.findById(weeklyPlanId).ifPresentOrElse(
                week -> week.updateTotals(planned, done, deferred, dropped, plannedMinutes, doneMinutes,
                        clock.instant()),
                () -> log.info("Ignoring action totals of unknown week {}", weeklyPlanId));
    }

    @Override
    @Transactional
    public void reviewed(UUID weeklyPlanId) {
        repository.findById(weeklyPlanId).ifPresent(week -> week.close(clock.instant()));
        scheduledJobService.cancelFor(weeklyPlanId);
    }
}
