package com.Travel.Buddy.dto.dispute;

import java.util.List;

/**
 * The claimant's own dispute list.
 *
 * <p>Rows are summary-level: evidence and timeline are only
 * loaded for the single dispute being opened, because attaching
 * them to every row would mean two extra queries per claim across
 * a page of results.
 */
public record DisputePageResponse(

        List<DisputeResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages
) {
    public static DisputePageResponse of(
            List<DisputeResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        return new DisputePageResponse(
                content, page, size, totalElements, totalPages
        );
    }
}