package com.Travel.Buddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a partner application request is invalid or attempts an
 * illegal state transition.
 *
 * <p>Carries its own HTTP status so the global handler can distinguish
 * a genuine conflict (409) from a bad request (400) or a missing
 * resource (404) without inspecting the message text.
 */
public class PartnerApplicationException
        extends RuntimeException {

    private final HttpStatus status;

    public PartnerApplicationException(
            HttpStatus status,
            String message
    ) {
        super(message);
        this.status = status;
    }

    public static PartnerApplicationException badRequest(
            String message
    ) {
        return new PartnerApplicationException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    public static PartnerApplicationException notFound(
            String message
    ) {
        return new PartnerApplicationException(
                HttpStatus.NOT_FOUND,
                message
        );
    }

    public static PartnerApplicationException conflict(
            String message
    ) {
        return new PartnerApplicationException(
                HttpStatus.CONFLICT,
                message
        );
    }

    public static PartnerApplicationException forbidden(
            String message
    ) {
        return new PartnerApplicationException(
                HttpStatus.FORBIDDEN,
                message
        );
    }

    public HttpStatus getStatus() {
        return status;
    }
}
