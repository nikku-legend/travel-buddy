package com.Travel.Buddy.service.property;

public class PropertyNotFoundException
        extends RuntimeException {

    public PropertyNotFoundException(
            String message
    ) {
        super(message);
    }
}