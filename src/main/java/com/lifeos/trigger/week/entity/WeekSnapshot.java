package com.lifeos.trigger.week.entity;

import com.lifeos.trigger.common.persistence.AssignedIdEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The latest numbers of a committed week, kept from Strategy's events (one user's events arrive in order). The
 * drift check reads them; Trigger does not keep the actions themselves.
 */
@Entity
@Table(name = "week_snapshot")
public class WeekSnapshot extends AssignedIdEntity {

    @Id
    private UUID weeklyPlanId;

    private UUID userId;
    private String isoWeek;
    private LocalDate weekStart;
    private int capacityMinutes;
    private int plannedCount;
    private int doneCount;
    private int deferredCount;
    private int droppedCount;
    private int plannedMinutes;
    private int doneMinutes;

    @Enumerated(EnumType.STRING)
    private WeekStatus status;

    @Version
    private int version;

    private Instant updatedAt;

    protected WeekSnapshot() {
    }

    /** A week was activated with {@code plannedCount} actions (all PLANNED) of {@code plannedMinutes}. */
    public static WeekSnapshot activated(UUID weeklyPlanId, UUID userId, String isoWeek, LocalDate weekStart,
            int capacityMinutes, int plannedCount, int plannedMinutes, Instant now) {
        WeekSnapshot week = new WeekSnapshot();
        week.weeklyPlanId = Objects.requireNonNull(weeklyPlanId);
        week.userId = Objects.requireNonNull(userId);
        week.isoWeek = Objects.requireNonNull(isoWeek);
        week.weekStart = Objects.requireNonNull(weekStart);
        week.capacityMinutes = capacityMinutes;
        week.plannedCount = plannedCount;
        week.plannedMinutes = plannedMinutes;
        week.status = WeekStatus.ACTIVE;
        week.updatedAt = now;
        return week;
    }

    /** The week's totals after an action changed (as {@code ActionStatusChanged.week} carries them). */
    public void updateTotals(int plannedCount, int doneCount, int deferredCount, int droppedCount,
            int plannedMinutes, int doneMinutes, Instant now) {
        this.plannedCount = plannedCount;
        this.doneCount = doneCount;
        this.deferredCount = deferredCount;
        this.droppedCount = droppedCount;
        this.plannedMinutes = plannedMinutes;
        this.doneMinutes = doneMinutes;
        this.updatedAt = now;
    }

    public void changeCapacity(int capacityMinutes, Instant now) {
        this.capacityMinutes = capacityMinutes;
        this.updatedAt = now;
    }

    /** Done minutes below this count as behind pace: {@code share} of the planned minutes, rounded down. */
    public int behindPaceThreshold(double share) {
        return (int) Math.floor(plannedMinutes * share);
    }

    /** An active week with planned work whose done work is below {@code share} of it. */
    public boolean isBehindPace(double share) {
        return status == WeekStatus.ACTIVE && plannedMinutes > 0 && doneMinutes < behindPaceThreshold(share);
    }

    public boolean isActive() {
        return status == WeekStatus.ACTIVE;
    }

    public void close(Instant now) {
        this.status = WeekStatus.CLOSED;
        this.updatedAt = now;
    }

    @Override
    public UUID getId() {
        return weeklyPlanId;
    }

    public UUID getWeeklyPlanId() {
        return weeklyPlanId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getIsoWeek() {
        return isoWeek;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public int getCapacityMinutes() {
        return capacityMinutes;
    }

    public int getPlannedCount() {
        return plannedCount;
    }

    public int getDoneCount() {
        return doneCount;
    }

    public int getDeferredCount() {
        return deferredCount;
    }

    public int getDroppedCount() {
        return droppedCount;
    }

    public int getPlannedMinutes() {
        return plannedMinutes;
    }

    public int getDoneMinutes() {
        return doneMinutes;
    }

    public WeekStatus getStatus() {
        return status;
    }
}
