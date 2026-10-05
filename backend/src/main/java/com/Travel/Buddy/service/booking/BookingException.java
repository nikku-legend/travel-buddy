package com.Travel.Buddy.service.booking;

public class BookingException
        extends RuntimeException {

    public BookingException(
            String message
    ) {
        super(message);
    }
}