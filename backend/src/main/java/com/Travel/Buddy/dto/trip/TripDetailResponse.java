package com.Travel.Buddy.dto.trip;

import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripSelectionType;
import com.Travel.Buddy.entity.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full trip detail, including the cart and the map.
 *
 * <p>Returned in one response because the planner screens
 * (review, checkout, active trip) all need the route, the cart
 * and the milestones together; splitting them would mean three
 * round trips and three chances for the UI to show inconsistent
 * state.
 */
public record TripDetailResponse(

        Long tripId,

        String title,

        TripStatus status,

        LocalDate startDate,

        LocalDate endDate,

        int nights,

        int plannedCityCount,

        /* ============================================================
         * SRS 2.3 wizard inputs. Nullable because the wizard
         * collects them one step at a time; a null means "not
         * chosen yet" rather than an error.
         * ============================================================ */
        Integer travelerCount,

        Integer adultCount,

        Integer childCount,

        int roomsRequired,

        String zone,

        String regionName,

        com.Travel.Buddy.entity.TravelStyle travelStyle,

        BigDecimal estimatedTotal,

        String currency,

        BigDecimal budgetAmount,

        boolean overBudget,

        BigDecimal budgetRemaining,

        LocalDateTime confirmedAt,

        LocalDateTime completedAt,

        List<TripCityResponse> cities,

        List<TripSelectionResponse> selections,

        List<TripRecommendationResponse> recommendations,

        List<TripMilestoneResponse> milestones
) {
    public static TripDetailResponse of(
            Trip t,
            List<TripCityResponse> cities,
            List<TripSelectionResponse> selections,
            List<TripRecommendationResponse> recommendations,
            List<TripMilestoneResponse> milestones
    ) {
        return new TripDetailResponse(
                t.getTripId(),
                t.getTitle(),
                t.getStatus(),
                t.getStartDate(),
                t.getEndDate(),
                t.nights(),
                t.getPlannedCityCount(),
                t.getTravelerCount(),
                t.getAdultCount(),
                t.getChildCount(),
                t.roomsRequired(),
                t.getZone(),
                t.getRegionName(),
                t.getTravelStyle(),
                t.getEstimatedTotal(),
                t.getCurrency(),
                t.getBudgetAmount(),
                t.isOverBudget(),
                t.budgetRemaining(),
                t.getConfirmedAt(),
                t.getCompletedAt(),
                cities,
                selections,
                recommendations,
                milestones
        );
    }
}