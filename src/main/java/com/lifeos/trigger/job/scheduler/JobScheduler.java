package com.lifeos.trigger.job.scheduler;

import com.lifeos.trigger.job.service.JobRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs due jobs every minute, batch after batch until none are left. A failing batch rolls back and is tried
 * again on the next pass; the other replicas keep running theirs.
 */
@Component
public class JobScheduler {

    static final int BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final JobRunService jobRunService;

    public JobScheduler(JobRunService jobRunService) {
        this.jobRunService = jobRunService;
    }

    @Scheduled(fixedDelayString = "${lifeos.trigger.runner.interval-ms:60000}",
            initialDelayString = "${lifeos.trigger.runner.initial-delay-ms:10000}")
    public void runDueJobs() {
        try {
            int ran;
            do {
                ran = jobRunService.runDue(BATCH_SIZE);
            } while (ran == BATCH_SIZE);
        } catch (RuntimeException e) {
            log.error("Running due jobs failed; trying again on the next pass", e);
        }
    }
}
