package com.Travel.Buddy.dto.review;

import java.math.BigDecimal;

/**
 * A subject's rating aggregate. (FR-26)
 *
 * <p>The histogram is included because an average alone hides the
 * shape of the feedback: 4.2 from a hundred reviews is a different
 * claim from 4.2 from three.
 */
public record ReviewSummaryResponse(
        String targetType,
        Long targetId,
        BigDecimal averageRating,
        long reviewCount,
        long fiveStar,
        long fourStar,
        long threeStar,
        long twoStar,
        long oneStar
) {

    /**
     * Percentage of reviews at the given star level, 0-100.
     */
    public int percentageFor(int stars) {

        if (reviewCount == 0) {
            return 0;
        }

        long bucket = switch (stars) {
            case 5 -> fiveStar;
            case 4 -> fourStar;
            case 3 -> threeStar;
            case 2 -> twoStar;
            case 1 -> oneStar;
            default -> 0;
        };

        return (int) Math.round(
                (bucket * 100.0) / reviewCount
        );
    }
}