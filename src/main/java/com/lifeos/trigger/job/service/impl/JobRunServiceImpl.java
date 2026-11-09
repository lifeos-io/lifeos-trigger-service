package com.lifeos.trigger.job.service.impl;

import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.trigger.job.entity.ScheduledJob;
import com.lifeos.trigger.job.event.DecisionReviewDueV1;
import com.lifeos.trigger.job.event.DriftDetectedV1;
import com.lifeos.trigger.job.event.ReopenTriggerFiredV1;
import com.lifeos.trigger.job.event.WeeklyReviewDueV1;
import com.lifeos.trigger.job.repository.ScheduledJobRepository;
import com.lifeos.trigger.job.service.JobRunService;
import com.lifeos.trigger.week.entity.WeekSnapshot;
import com.lifeos.trigger.week.repository.WeekSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claims due jobs with {@code FOR UPDATE SKIP LOCKED}, so replicas (or overlapping runs) never run the same job;
 * a job, its event and its FIRED status commit together. A job that was due while Trigger was down runs on the
 * next pass, late: its event carries the {@code dueAt} it was meant for.
 */
@Service
public class JobRunServiceImpl implements JobRunService {

    private static final Logger log = LoggerFactory.getLogger(JobRunServiceImpl.class);

    private final ScheduledJobRepository repository;
    private final WeekSnapshotRepository weekSnapshotRepository;
    private final Outbox outbox;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final double behindPaceShare;

    public JobRunServiceImpl(ScheduledJobRepository repository, WeekSnapshotRepository weekSnapshotRepository,
            Outbox outbox, JsonMapper jsonMapper, Clock clock,
            @Value("${lifeos.trigger.drift.behind-pace-share:0.5}") double behindPaceShare) {
        this.repository = repository;
        this.weekSnapshotRepository = weekSnapshotRepository;
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.behindPaceShare = behindPaceShare;
    }

    @Override
    @Transactional
    public int runDue(int limit) {
        Instant now = clock.instant();
        List<ScheduledJob> due = repository.claimDue(now, limit);
        for (ScheduledJob job : due) {
            run(job, now);
            job.fire(now);
        }
        return due.size();
    }

    private void run(ScheduledJob job, Instant now) {
        JsonNode payload = jsonMapper.readTree(job.getPayload());
        switch (job.getKind()) {
            case DECISION_REVIEW -> publish(job, DecisionReviewDueV1.TYPE, DecisionReviewDueV1.VERSION,
                    new DecisionReviewDueV1(job.getSubjectId(), uuidOrNull(payload.get("goalId")),
                            payload.get("title").asString(), LocalDate.parse(payload.get("reviewOn").asString()),
                            job.getDueAt()));
            case REOPEN_TRIGGER -> publish(job, ReopenTriggerFiredV1.TYPE, ReopenTriggerFiredV1.VERSION,
                    new ReopenTriggerFiredV1(job.getSubjectId(), job.getSubKey(),
                            payload.get("description").asString(), LocalDate.parse(payload.get("firesOn").asString()),
                            job.getDueAt()));
            case WEEKLY_REVIEW -> activeWeek(job).ifPresent(week -> publish(job, WeeklyReviewDueV1.TYPE,
                    WeeklyReviewDueV1.VERSION,
                    new WeeklyReviewDueV1(week.getWeeklyPlanId(), week.getIsoWeek(), week.getWeekStart(),
                            job.getDueAt())));
            case DRIFT_CHECK -> activeWeek(job)
                    .filter(week -> week.isBehindPace(behindPaceShare))
                    .ifPresent(week -> publish(job, DriftDetectedV1.TYPE, DriftDetectedV1.VERSION,
                            new DriftDetectedV1(week.getWeeklyPlanId(), week.getIsoWeek(),
                                    DriftDetectedV1.Kind.BEHIND_PACE, week.getCapacityMinutes(),
                                    week.getPlannedMinutes(), week.getDoneMinutes(),
                                    week.behindPaceThreshold(behindPaceShare), week.getDeferredCount(), now)));
        }
    }

    /** The job's week if it is still running; a reviewed week is normally cancelled already, this is the backstop. */
    private Optional<WeekSnapshot> activeWeek(ScheduledJob job) {
        Optional<WeekSnapshot> week = weekSnapshotRepository.findById(job.getSubjectId())
                .filter(WeekSnapshot::isActive);
        if (week.isEmpty()) {
            log.info("Job {} ({}) has no active week {}; nothing to publish", job.getId(), job.getKind(),
                    job.getSubjectId());
        }
        return week;
    }

    private void publish(ScheduledJob job, String type, int version, Object payload) {
        outbox.append(new OutboxMessage("ScheduledJob", job.getId(), job.getUserId(), type, version, payload));
    }

    private static UUID uuidOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : UUID.fromString(node.asString());
    }
}
