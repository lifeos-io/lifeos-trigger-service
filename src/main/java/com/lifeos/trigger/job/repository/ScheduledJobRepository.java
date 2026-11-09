package com.lifeos.trigger.job.repository;

import com.lifeos.trigger.job.entity.JobKind;
import com.lifeos.trigger.job.entity.JobStatus;
import com.lifeos.trigger.job.entity.ScheduledJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, UUID> {

    Optional<ScheduledJob> findByKindAndSubjectIdAndSubKey(JobKind kind, UUID subjectId, int subKey);

    /** The jobs about a decision or a weekly plan in {@code status}. */
    List<ScheduledJob> findBySubjectIdAndStatus(UUID subjectId, JobStatus status);

    List<ScheduledJob> findByUserIdAndStatus(UUID userId, JobStatus status);

    /** The user's pending jobs, soonest first. */
    List<ScheduledJob> findByUserIdAndStatusOrderByDueAtAsc(UUID userId, JobStatus status);

    /**
     * Claims up to {@code limit} due jobs for this transaction: rows another replica (or run) holds are skipped,
     * not waited for, so every due job is run by exactly one of them.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM scheduled_job
            WHERE status = 'PENDING' AND due_at <= :now
            ORDER BY due_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
    List<ScheduledJob> claimDue(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM ScheduledJob j WHERE j.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
