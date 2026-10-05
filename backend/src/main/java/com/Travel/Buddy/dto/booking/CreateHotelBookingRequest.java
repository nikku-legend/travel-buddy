package com.Travel.Buddy.dto.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateHotelBookingRequest(

        @NotNull(message = "Room type is required")
        Long roomTypeId,

        @NotNull(message = "Check-in date is required")
        LocalDate checkIn,

        @NotNull(message = "Check-out date is required")
        LocalDate checkOut,

        @NotNull(message = "Number of guests is required")
        @Min(
                value = 1,
                message = "At least one guest is required"
        )
        Integer guests,

        @NotNull(message = "Number of rooms is required")
        @Min(
                value = 1,
                message = "At least one room is required"
        )
        Integer rooms,

        @NotBlank(message = "Lead guest name is required")
        String guestName,

        @NotBlank(message = "Lead guest email is required")
        @Email(message = "Lead guest email must be valid")
        String guestEmail,

        @NotBlank(message = "Lead guest phone is required")
        String guestPhone,

        String specialRequests

) {
}
