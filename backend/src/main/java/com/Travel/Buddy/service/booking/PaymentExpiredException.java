package com.Travel.Buddy.service.booking;

public class PaymentExpiredException extends RuntimeException {

    public PaymentExpiredException(String message) {
        super(message);
    }
}
