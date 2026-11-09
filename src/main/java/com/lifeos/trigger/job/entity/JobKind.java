package com.lifeos.trigger.job.entity;

/** What a scheduled job publishes when it is due. */
public enum JobKind {
    /** {@code DecisionReviewDue} on a decision's review date. */
    DECISION_REVIEW,
    /** {@code ReopenTriggerFired} on a DATE reopen trigger's date; {@code subKey} is the trigger's position. */
    REOPEN_TRIGGER,
    /** {@code WeeklyReviewDue} on the active week's Sunday evening. */
    WEEKLY_REVIEW,
    /** The mid-week drift check (Thursday evening); publishes {@code DriftDetected} only when behind pace. */
    DRIFT_CHECK
}
