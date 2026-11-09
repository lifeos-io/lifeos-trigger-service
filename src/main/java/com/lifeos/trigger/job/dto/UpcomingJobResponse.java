package com.lifeos.trigger.job.dto;

import com.lifeos.trigger.job.entity.JobKind;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Something Trigger will tell the user about.
 *
 * @param subjectId the decision or weekly plan it is about
 * @param dueOn     the user's local date
 * @param dueTime   the user's local time
 * @param dueAt     the same moment as an instant
 * @param label     what it is about: the decision's title, the trigger's description or the ISO week
 */
public record UpcomingJobResponse(
        UUID id,
        JobKind kind,
        UUID subjectId,
        LocalDate dueOn,
        LocalTime dueTime,
        Instant dueAt,
        String label) {
}
