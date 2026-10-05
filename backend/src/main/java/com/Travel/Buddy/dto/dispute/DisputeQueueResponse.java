package com.Travel.Buddy.dto.dispute;

import java.math.BigDecimal;
import java.util.List;

/**
 * The admin work queue.
 *
 * <p>Carries the live-dispute count and the total already
 * refunded in the same response, because the queue header shows
 * both and a separate stats call would race with the rows.
 */
public record DisputeQueueResponse(

        List<DisputeSummaryResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages,

        long openCount,

        BigDecimal totalRefunded
) {
    public static DisputeQueueResponse of(
            List<DisputeSummaryResponse> content
    ) {
        return new DisputeQueueResponse(
                content, 0, 0, content.size(), 1, content.size(),
                BigDecimal.ZERO
        );
    }
}