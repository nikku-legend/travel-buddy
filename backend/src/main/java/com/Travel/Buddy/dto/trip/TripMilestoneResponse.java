package com.Travel.Buddy.dto.trip;

import com.Travel.Buddy.entity.TripMilestone;
import com.Travel.Buddy.entity.TripMilestoneType;

import java.time.LocalDateTime;

public record TripMilestoneResponse(

        Long milestoneId,

        TripMilestoneType milestoneType,

        String label,

        int progressPercent,

        /*
         * Drives the map animation, and is only true once
         * something real happened. TP-11 requires the map to
         * reflect actual progress rather than a scripted
         * animation.
         */
        boolean completed,

        LocalDateTime completedAt
) {
    public static TripMilestoneResponse of(
            TripMilestone m
    ) {
        return new TripMilestoneResponse(
                m.getMilestoneId(),
                m.getMilestoneType(),
                m.getLabel(),
                m.getProgressPercent(),
                m.isCompleted(),
                m.getCompletedAt()
        );
    }
}