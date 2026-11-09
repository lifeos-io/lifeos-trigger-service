package com.lifeos.trigger.job.service.impl;

import com.lifeos.trigger.job.dto.UpcomingJobResponse;
import com.lifeos.trigger.job.entity.JobKind;
import com.lifeos.trigger.job.entity.JobStatus;
import com.lifeos.trigger.job.entity.ScheduledJob;
import com.lifeos.trigger.job.mapper.ScheduledJobMapper;
import com.lifeos.trigger.job.repository.ScheduledJobRepository;
import com.lifeos.trigger.job.service.ScheduledJobService;
import com.lifeos.trigger.setting.service.UserSettingService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ScheduledJobServiceImpl implements ScheduledJobService {

    private final ScheduledJobRepository repository;
    private final UserSettingService userSettingService;
    private final ScheduledJobMapper scheduledJobMapper;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final LocalTime decisionTime;
    private final LocalTime weeklyReviewTime;
    private final LocalTime driftCheckTime;

    public ScheduledJobServiceImpl(ScheduledJobRepository repository, UserSettingService userSettingService,
            ScheduledJobMapper scheduledJobMapper, JsonMapper jsonMapper, Clock clock,
            @Value("${lifeos.trigger.schedule.decision-time:09:00}") String decisionTime,
            @Value("${lifeos.trigger.schedule.weekly-review-time:18:00}") String weeklyReviewTime,
            @Value("${lifeos.trigger.schedule.drift-check-time:18:00}") String driftCheckTime) {
        this.repository = repository;
        this.userSettingService = userSettingService;
        this.scheduledJobMapper = scheduledJobMapper;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.decisionTime = LocalTime.parse(decisionTime);
        this.weeklyReviewTime = LocalTime.parse(weeklyReviewTime);
        this.driftCheckTime = LocalTime.parse(driftCheckTime);
    }

    @Override
    @Transactional
    public void scheduleDecisionReview(UUID userId, UUID decisionId, UUID goalId, String title, LocalDate reviewOn) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("goalId", goalId);
        payload.put("title", title);
        payload.put("reviewOn", reviewOn.toString());
        schedule(userId, JobKind.DECISION_REVIEW, decisionId, 0, reviewOn, decisionTime, payload);
    }

    @Override
    @Transactional
    public void scheduleReopenTrigger(UUID userId, UUID decisionId, int position, String description,
            LocalDate firesOn) {
        schedule(userId, JobKind.REOPEN_TRIGGER, decisionId, position, firesOn, decisionTime,
                Map.of("description", description, "firesOn", firesOn.toString()));
    }

    @Override
    @Transactional
    public void scheduleWeek(UUID userId, UUID weeklyPlanId, String isoWeek, LocalDate weekStart) {
        Map<String, Object> payload = Map.of("isoWeek", isoWeek, "weekStart", weekStart.toString());
        schedule(userId, JobKind.WEEKLY_REVIEW, weeklyPlanId, 0, weekStart.plusDays(6), weeklyReviewTime, payload);
        schedule(userId, JobKind.DRIFT_CHECK, weeklyPlanId, 0, weekStart.plusDays(3), driftCheckTime, payload);
    }

    @Override
    @Transactional
    public void cancelFor(UUID subjectId) {
        Instant now = clock.instant();
        repository.findBySubjectIdAndStatus(subjectId, JobStatus.PENDING).forEach(job -> job.cancel(now));
    }

    @Override
    @Transactional
    public void moveTo(UUID userId, ZoneId zone) {
        Instant now = clock.instant();
        repository.findByUserIdAndStatus(userId, JobStatus.PENDING).forEach(job -> job.moveTo(zone, now));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UpcomingJobResponse> upcoming(UUID userId) {
        return repository.findByUserIdAndStatusOrderByDueAtAsc(userId, JobStatus.PENDING).stream()
                .filter(job -> job.getKind() != JobKind.DRIFT_CHECK)
                .map(scheduledJobMapper::toUpcomingResponse)
                .toList();
    }

    /** Creates the job unless it exists (a redelivered or repeated event). */
    private void schedule(UUID userId, JobKind kind, UUID subjectId, int subKey, LocalDate dueOn, LocalTime dueTime,
            Map<String, ?> payload) {
        if (repository.findByKindAndSubjectIdAndSubKey(kind, subjectId, subKey).isPresent()) {
            return;
        }
        repository.save(ScheduledJob.schedule(userId, kind, subjectId, subKey, dueOn, dueTime,
                userSettingService.zoneOf(userId), jsonMapper.writeValueAsString(payload), clock.instant()));
    }
}
