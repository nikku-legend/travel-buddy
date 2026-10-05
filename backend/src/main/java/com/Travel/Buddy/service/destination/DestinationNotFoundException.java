package com.Travel.Buddy.service.destination;

public class DestinationNotFoundException
        extends RuntimeException {

    public DestinationNotFoundException(
            String message
    ) {
        super(message);
    }
}