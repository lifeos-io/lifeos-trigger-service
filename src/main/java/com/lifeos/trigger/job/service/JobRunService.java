package com.lifeos.trigger.job.service;

/** Runs the jobs that are due: each publishes its event (or decides not to) and is marked FIRED. */
public interface JobRunService {

    /**
     * Claims and runs up to {@code limit} due jobs in one transaction (with their outbox events).
     *
     * @return how many jobs ran; fewer than {@code limit} means none are left for now
     */
    int runDue(int limit);
}
