package com.Travel.Buddy.exception;

import com.Travel.Buddy.service.booking.BookingException;
import com.Travel.Buddy.service.booking.PaymentExpiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.ServletRequestBindingException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Unhandled failures are logged in full.
     *
     * <p>Without this the fallback handler returned a bare 500 with no
     * server-side record, which makes production 500s undiagnosable.
     */
    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);


    /*
     * ============================================================
     * BOOKING EXCEPTION
     * ============================================================
     */

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<Map<String, Object>>
    handleBookingException(
            BookingException exception
    ) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }


    /*
     * ============================================================
     * PAYMENT EXPIRED
     * ============================================================
     */

    @ExceptionHandler(PaymentExpiredException.class)
    public ResponseEntity<Map<String, Object>>
    handlePaymentExpiredException(
            PaymentExpiredException exception
    ) {

        return buildResponse(
                HttpStatus.GONE,
                exception.getMessage()
        );
    }


    /*
     * ============================================================
     * PAYMENT VERIFICATION
     * ============================================================
     */

    @ExceptionHandler(
            PaymentVerificationException.class
    )
    public ResponseEntity<Map<String, Object>>
    handlePaymentVerificationException(
            PaymentVerificationException exception
    ) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }


    /*
     * ============================================================
     * VALIDATION ERRORS
     * ============================================================
     */

    @ExceptionHandler(
            MethodArgumentNotValidException.class
    )
    public ResponseEntity<Map<String, Object>>
    handleValidationException(
            MethodArgumentNotValidException exception
    ) {

        String message =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .findFirst()
                        .map(error ->
                                error.getDefaultMessage()
                        )
                        .orElse(
                                "Request validation failed"
                        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                message
        );
    }


    /*
     * ============================================================
     * ILLEGAL ARGUMENT
     * ============================================================
     */

    @ExceptionHandler(
            IllegalArgumentException.class
    )
    public ResponseEntity<Map<String, Object>>
    handleIllegalArgumentException(
            IllegalArgumentException exception
    ) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }


    /*
     * ============================================================
     * ILLEGAL STATE
     * ============================================================
     */

    @ExceptionHandler(
            IllegalStateException.class
    )
    public ResponseEntity<Map<String, Object>>
    handleIllegalStateException(
            IllegalStateException exception
    ) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }


    /*
     * ============================================================
     * PARTNER APPLICATION EXCEPTIONS
     * ============================================================
     */

    @ExceptionHandler(PartnerApplicationException.class)
    public ResponseEntity<Map<String, Object>>
    handlePartnerApplicationException(
            PartnerApplicationException exception
    ) {

        return buildResponse(
                exception.getStatus(),
                exception.getMessage()
        );
    }


    /*
     * ============================================================
     * AUTHENTICATION FAILURE
     *
     * A wrong password or unknown account is a 401, not a server
     * fault. Without this handler the generic fallback below turned
     * every mistyped password into a 500 "An unexpected error
     * occurred", which both misleads the user and hides real outages
     * from monitoring.
     * ============================================================
     */

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>>
    handleAuthenticationException(
            AuthenticationException exception
    ) {

        log.info(
                "Authentication failure: {}",
                exception.getMessage()
        );

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
        );
    }


    /*
     * ============================================================
     * ACCESS DENIED
     * ============================================================
     */

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>>
    handleAccessDeniedException(
            AccessDeniedException exception
    ) {

        return buildResponse(
                HttpStatus.FORBIDDEN,
                exception.getMessage() != null
                        ? exception.getMessage()
                        : "You do not have permission to perform this action"
        );
    }


    /*
     * ============================================================
     * MALFORMED REQUESTS
     *
     * The catch-all handler below would otherwise turn every binding
     * failure into a 500. A missing query parameter, a wrong parameter
     * type or an unreadable body is a client error and must stay a
     * 400, otherwise monitoring fills with false server faults and
     * callers are told to retry something that can never succeed.
     * ============================================================
     */

    @ExceptionHandler({
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<Map<String, Object>>
    handleMalformedRequest(
            Exception exception
    ) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }

    @ExceptionHandler(
            HttpRequestMethodNotSupportedException.class
    )
    public ResponseEntity<Map<String, Object>>
    handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception
    ) {

        return buildResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method not allowed: " + exception.getMethod()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>>
    handleNotFound(
            NoResourceFoundException exception
    ) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Resource not found"
        );
    }

    /*
     * ============================================================
     * FALLBACK
     * ============================================================
     */

    @ExceptionHandler(
            Exception.class
    )
    public ResponseEntity<Map<String, Object>>
    handleGenericException(
            Exception exception
    ) {

        log.error(
                "Unhandled exception while processing request",
                exception
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
    }


    /*
     * ============================================================
     * RESPONSE BUILDER
     * ============================================================
     */

    private ResponseEntity<Map<String, Object>>
    buildResponse(
            HttpStatus status,
            String message
    ) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "timestamp",
                LocalDateTime.now()
        );

        response.put(
                "status",
                status.value()
        );

        response.put(
                "error",
                status.getReasonPhrase()
        );

        response.put(
                "message",
                message != null
                        ? message
                        : "Request failed"
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }
}