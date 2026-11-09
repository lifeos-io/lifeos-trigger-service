package com.lifeos.trigger.job.entity;

/** PENDING → FIRED (it ran) or CANCELLED (its subject was closed, superseded or reviewed first). */
public enum JobStatus {
    PENDING,
    FIRED,
    CANCELLED
}
