package com.Travel.Buddy.dto.trip;

import com.Travel.Buddy.entity.TripRecommendation;
import com.Travel.Buddy.entity.TripRecommendationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TripRecommendationResponse(

        Long recommendationId,

        TripRecommendationType recommendationType,

        Long tripCityId,

        String cityName,

        Long targetId,

        /*
         * The room this hotel offer refers to. Without it a
         * client can add the property but not the room, and the
         * cart prices a hotel from its room type, so the stay
         * would quote zero and checkout would refuse the trip as
         * an empty cart.
         */
        Long roomTypeId,

        String title,

        BigDecimal quotedAmount,

        String currency,

        Integer rankPosition,

        BigDecimal distanceKm,

        /*
         * The section 4 justification, shown verbatim: "2.1 km
         * from your selected place". Persisted rather than
         * recomputed, so the number and the sentence cannot
         * disagree.
         */
        String reason,

        /*
         * Why this was considered and dropped. Null when it is a
         * live recommendation.
         */
        String rejectionReason,

        /*
         * Whether this row is a live recommendation or a
         * candidate the engine considered and rejected.
         *
         * <p>Deliberately explicit rather than inferred from a
         * null rank or a null reason. Section 4.1 wants rejected
         * candidates kept for transparency, which means the
         * response carries both kinds, and a client guessing
         * which is which from a null would eventually render a
         * rejected hotel with a blank price as though it were a
         * real option.
         */
        boolean recommended,

        boolean shown,

        Boolean accepted,

        LocalDateTime createdAt
) {
    public static TripRecommendationResponse of(
            TripRecommendation r,
            String cityName
    ) {
        return new TripRecommendationResponse(
                r.getRecommendationId(),
                r.getRecommendationType(),
                r.getTripCity() == null
                        ? null
                        : r.getTripCity().getTripCityId(),
                cityName,
                r.getTargetId(),
                r.getRoomType() == null
                        ? null
                        : r.getRoomType().getRoomTypeId(),
                r.getTitle(),
                r.getQuotedAmount(),
                r.getCurrency(),
                r.getRankPosition(),
                r.getDistanceKm(),
                r.getReason(),
                r.getRejectionReason(),

                /*
                 * A live recommendation always carries a rank; a
                 * rejected candidate never does. Taken from the
                 * rank rather than recomputed, so the flag and the
                 * ordering cannot disagree.
                 */
                r.getRankPosition() != null,
                r.isShown(),
                r.getAccepted(),
                r.getCreatedAt()
        );
    }
}