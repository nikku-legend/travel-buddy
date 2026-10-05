package com.Travel.Buddy.dto.trip;

import com.Travel.Buddy.entity.TripSelection;
import com.Travel.Buddy.entity.TripSelectionStatus;
import com.Travel.Buddy.entity.TripSelectionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TripSelectionResponse(

        Long selectionId,

        TripSelectionType selectionType,

        TripSelectionStatus status,

        Long tripCityId,

        String cityName,

        Long targetId,

        String targetName,

        Long roomTypeId,

        String roomTypeName,

        Long placeId,

        String placeName,

        LocalDate checkIn,

        LocalDate checkOut,

        Integer guests,

        int nights,

        BigDecimal quotedAmount,

        String currency,

        Long bookingId,

        String bookingReference,

        /*
         * Always false. Selections reserve nothing, and this
         * field exists so no client can present one as a
         * confirmed booking.
         */
        boolean reserved,

        String unavailabilityReason,

        LocalDateTime createdAt
) {
    public static TripSelectionResponse of(
            TripSelection s,
            String targetName,
            String cityName,
            String roomTypeName,
            String placeName
    ) {
        return new TripSelectionResponse(
                s.getSelectionId(),
                s.getSelectionType(),
                s.getStatus(),
                s.getTripCity() == null
                        ? null
                        : s.getTripCity().getTripCityId(),
                cityName,
                s.getTargetId(),
                targetName,
                s.getRoomType() == null
                        ? null
                        : s.getRoomType().getRoomTypeId(),
                roomTypeName,
                s.getPlace() == null
                        ? null
                        : s.getPlace().getPlaceId(),
                placeName,
                s.getCheckIn(),
                s.getCheckOut(),
                s.getGuests(),
                s.nights(),
                s.getQuotedAmount(),
                s.getCurrency(),
                s.getBooking() == null
                        ? null
                        : s.getBooking().getBookingId(),
                s.getBooking() == null
                        ? null
                        : s.getBooking().getBookingReference(),
                false,
                s.getUnavailabilityReason(),
                s.getCreatedAt()
        );
    }
}