package com.Travel.Buddy.dto.booking;

import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookingResponse(

        Long bookingId,

        String bookingReference,

        Long userId,

        Long propertyId,

        String propertyName,

        Long roomTypeId,

        String roomTypeName,

        LocalDate checkIn,

        LocalDate checkOut,

        Integer guests,

        Integer rooms,

        Integer numberOfNights,

        BigDecimal totalAmount,

        String currency,

        BookingStatus bookingStatus,

        PaymentStatus paymentStatus,

        LocalDateTime holdExpiresAt,

        LocalDateTime createdAt

) {
}
