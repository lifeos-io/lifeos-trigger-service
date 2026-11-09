package com.lifeos.trigger.strategyevents.consumer;

import com.lifeos.base.messaging.MalformedEventException;
import com.lifeos.base.messaging.inbox.Inbox;
import com.lifeos.base.messaging.inbox.IncomingEvent;
import com.lifeos.trigger.account.service.AccountDeletionService;
import com.lifeos.trigger.job.service.ScheduledJobService;
import com.lifeos.trigger.setting.service.UserSettingService;
import com.lifeos.trigger.strategyevents.event.ActionStatusChangedV1;
import com.lifeos.trigger.strategyevents.event.CapacityChangedV1;
import com.lifeos.trigger.strategyevents.event.DecisionClosedV1;
import com.lifeos.trigger.strategyevents.event.DecisionRecordedV1;
import com.lifeos.trigger.strategyevents.event.DecisionSupersededV1;
import com.lifeos.trigger.strategyevents.event.UserDeletionRequestedV1;
import com.lifeos.trigger.strategyevents.event.UserProfileUpdatedV1;
import com.lifeos.trigger.strategyevents.event.WeeklyPlanActivatedV1;
import com.lifeos.trigger.strategyevents.event.WeeklyReviewCompletedV1;
import com.lifeos.trigger.week.service.WeekSnapshotService;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/**
 * Trigger's view of Strategy: every event of {@code lifeos.strategy.events.v1} once (through the inbox, in one
 * transaction with what it changes), handed to the module it concerns. Types Trigger does not use are marked
 * processed and ignored; a malformed event goes to the dead-letter topic.
 */
@Component
public class StrategyEventConsumer {

    private final Inbox inbox;
    private final ScheduledJobService scheduledJobService;
    private final WeekSnapshotService weekSnapshotService;
    private final UserSettingService userSettingService;
    private final AccountDeletionService accountDeletionService;

    public StrategyEventConsumer(Inbox inbox, ScheduledJobService scheduledJobService,
            WeekSnapshotService weekSnapshotService, UserSettingService userSettingService,
            AccountDeletionService accountDeletionService) {
        this.inbox = inbox;
        this.scheduledJobService = scheduledJobService;
        this.weekSnapshotService = weekSnapshotService;
        this.userSettingService = userSettingService;
        this.accountDeletionService = accountDeletionService;
    }

    // Own retry and dead-letter topics: other services consume this topic with retries of their own.
    @RetryableTopic(exclude = MalformedEventException.class, retryTopicSuffix = "-trigger-retry",
            dltTopicSuffix = "-trigger-dlt")
    @KafkaListener(topics = "${lifeos.trigger.strategy-topic:lifeos.strategy.events.v1}", groupId = "trigger")
    public void on(ConsumerRecord<String, String> record) {
        inbox.handle(record, this::dispatch);
    }

    private void dispatch(IncomingEvent event) {
        if (event.eventVersion() != 1) {
            return;
        }
        UUID userId = event.userId();
        switch (event.eventType()) {
            case UserProfileUpdatedV1.TYPE -> {
                String timezone = event.payloadAs(UserProfileUpdatedV1.class).timezone();
                if (userSettingService.timezoneChanged(userId, timezone)) {
                    scheduledJobService.moveTo(userId, ZoneId.of(timezone));
                }
            }
            case DecisionRecordedV1.TYPE -> decisionRecorded(userId, event.payloadAs(DecisionRecordedV1.class));
            case DecisionClosedV1.TYPE ->
                    scheduledJobService.cancelFor(event.payloadAs(DecisionClosedV1.class).decisionId());
            case DecisionSupersededV1.TYPE ->
                    scheduledJobService.cancelFor(event.payloadAs(DecisionSupersededV1.class).decisionId());
            case WeeklyPlanActivatedV1.TYPE -> {
                WeeklyPlanActivatedV1 week = event.payloadAs(WeeklyPlanActivatedV1.class);
                weekSnapshotService.activated(userId, week.weeklyPlanId(), week.isoWeek(), week.weekStart(),
                        week.capacityMinutes(), week.plannedActionCount(), week.plannedActionMinutes());
            }
            case CapacityChangedV1.TYPE -> {
                CapacityChangedV1 change = event.payloadAs(CapacityChangedV1.class);
                weekSnapshotService.capacityChanged(change.weeklyPlanId(), change.capacityMinutes());
            }
            case ActionStatusChangedV1.TYPE -> {
                ActionStatusChangedV1 change = event.payloadAs(ActionStatusChangedV1.class);
                ActionStatusChangedV1.WeekV1 week = change.week();
                weekSnapshotService.totalsChanged(change.weeklyPlanId(), week.planned(), week.done(),
                        week.deferred(), week.dropped(), week.plannedMinutes(), week.doneMinutes());
            }
            case WeeklyReviewCompletedV1.TYPE ->
                    weekSnapshotService.reviewed(event.payloadAs(WeeklyReviewCompletedV1.class).weeklyPlanId());
            case UserDeletionRequestedV1.TYPE -> accountDeletionService.deleteAll(userId);
            default -> {
                // Not something Trigger acts on.
            }
        }
    }

    /** The review date, and each DATE reopen trigger by its position; EVENT triggers are not Trigger's to see. */
    private void decisionRecorded(UUID userId, DecisionRecordedV1 decision) {
        scheduledJobService.scheduleDecisionReview(userId, decision.decisionId(), decision.goalId(), decision.title(),
                decision.reviewOn());
        List<DecisionRecordedV1.TriggerV1> triggers = decision.reopenTriggers() == null ? List.of()
                : decision.reopenTriggers();
        for (int position = 0; position < triggers.size(); position++) {
            DecisionRecordedV1.TriggerV1 trigger = triggers.get(position);
            if ("DATE".equals(trigger.kind()) && trigger.firesOn() != null) {
                scheduledJobService.scheduleReopenTrigger(userId, decision.decisionId(), position,
                        trigger.description(), trigger.firesOn());
            }
        }
    }
}
