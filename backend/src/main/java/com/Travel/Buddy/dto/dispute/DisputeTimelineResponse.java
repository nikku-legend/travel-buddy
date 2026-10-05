package com.Travel.Buddy.dto.dispute;

import com.Travel.Buddy.entity.DisputeEventType;
import com.Travel.Buddy.entity.DisputeTimelineEntry;

import java.time.LocalDateTime;

public record DisputeTimelineResponse(

        Long timelineId,

        DisputeEventType eventType,

        String actorName,

        String fromStatus,

        String toStatus,

        String note,

        LocalDateTime createdAt
) {
    public static DisputeTimelineResponse from(
            DisputeTimelineEntry e
    ) {
        return new DisputeTimelineResponse(
                e.getTimelineId(),
                e.getEventType(),
                e.getActor() == null
                        ? null
                        : e.getActor().getFullName(),
                e.getFromStatus(),
                e.getToStatus(),
                e.getNote(),
                e.getCreatedAt()
        );
    }
}