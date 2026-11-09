package com.lifeos.trigger.job.entity;

import com.lifeos.base.id.UuidV7;
import com.lifeos.trigger.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Something Trigger publishes at a moment in the user's life: the local date and time ({@code dueOn},
 * {@code dueTime}) are what was asked for; {@code dueAt} is that moment in the user's time zone, recomputed when
 * the zone changes. A job runs once (FIRED) or is CANCELLED when its subject no longer needs it.
 */
@Entity
@Table(name = "scheduled_job")
public class ScheduledJob extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private JobKind kind;

    private UUID subjectId;
    private int subKey;
    private LocalDate dueOn;
    private LocalTime dueTime;
    private Instant dueAt;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    private Instant firedAt;
    private Instant cancelledAt;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    protected ScheduledJob() {
    }

    /** @param payload JSON object with what the published event needs beyond the job itself */
    public static ScheduledJob schedule(UUID userId, JobKind kind, UUID subjectId, int subKey, LocalDate dueOn,
            LocalTime dueTime, ZoneId zone, String payload, Instant now) {
        ScheduledJob job = new ScheduledJob();
        job.id = UuidV7.next();
        job.userId = Objects.requireNonNull(userId);
        job.kind = Objects.requireNonNull(kind);
        job.subjectId = Objects.requireNonNull(subjectId);
        job.subKey = subKey;
        job.dueOn = Objects.requireNonNull(dueOn);
        job.dueTime = Objects.requireNonNull(dueTime);
        job.dueAt = dueOn.atTime(dueTime).atZone(zone).toInstant();
        job.payload = Objects.requireNonNull(payload);
        job.status = JobStatus.PENDING;
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    /** The user moved to another time zone: the same local date and time, a different instant. */
    public boolean moveTo(ZoneId zone, Instant now) {
        Instant moved = dueOn.atTime(dueTime).atZone(zone).toInstant();
        if (status != JobStatus.PENDING || moved.equals(dueAt)) {
            return false;
        }
        this.dueAt = moved;
        this.updatedAt = now;
        return true;
    }

    /** PENDING → FIRED. */
    public void fire(Instant now) {
        requirePending();
        this.status = JobStatus.FIRED;
        this.firedAt = now;
        this.updatedAt = now;
    }

    /** PENDING → CANCELLED; a job that already ran or was cancelled stays as it is. */
    public boolean cancel(Instant now) {
        if (status != JobStatus.PENDING) {
            return false;
        }
        this.status = JobStatus.CANCELLED;
        this.cancelledAt = now;
        this.updatedAt = now;
        return true;
    }

    private void requirePending() {
        if (status != JobStatus.PENDING) {
            throw new IllegalStateException("Job " + id + " is " + status + ", not PENDING");
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public JobKind getKind() {
        return kind;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public int getSubKey() {
        return subKey;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public String getPayload() {
        return payload;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Instant getFiredAt() {
        return firedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
