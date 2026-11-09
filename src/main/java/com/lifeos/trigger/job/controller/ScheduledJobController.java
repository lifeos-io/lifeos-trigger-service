package com.lifeos.trigger.job.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.trigger.job.dto.UpcomingJobResponse;
import com.lifeos.trigger.job.service.ScheduledJobService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/triggers}: what Trigger has scheduled for the signed-in user. */
@RestController
@RequestMapping("/api/triggers")
public class ScheduledJobController {

    private final ScheduledJobService scheduledJobService;

    public ScheduledJobController(ScheduledJobService scheduledJobService) {
        this.scheduledJobService = scheduledJobService;
    }

    /** Pending reminders (decision reviews, reopen triggers, weekly review), soonest first. */
    @GetMapping("/upcoming")
    public List<UpcomingJobResponse> upcoming(@CurrentUser UUID userId) {
        return scheduledJobService.upcoming(userId);
    }
}
