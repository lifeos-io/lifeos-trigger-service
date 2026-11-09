package com.lifeos.trigger.job.mapper;

import com.lifeos.trigger.job.dto.UpcomingJobResponse;
import com.lifeos.trigger.job.entity.ScheduledJob;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ScheduledJobMapper {

    private final JsonMapper jsonMapper;

    public ScheduledJobMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public UpcomingJobResponse toUpcomingResponse(ScheduledJob job) {
        return new UpcomingJobResponse(job.getId(), job.getKind(), job.getSubjectId(), job.getDueOn(),
                job.getDueTime(), job.getDueAt(), label(job));
    }

    private String label(ScheduledJob job) {
        JsonNode payload = jsonMapper.readTree(job.getPayload());
        String field = switch (job.getKind()) {
            case DECISION_REVIEW -> "title";
            case REOPEN_TRIGGER -> "description";
            case WEEKLY_REVIEW, DRIFT_CHECK -> "isoWeek";
        };
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
