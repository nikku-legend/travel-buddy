package com.Travel.Buddy.service.payment;

import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.exception.PaymentVerificationException;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class PaymentService {

    private final boolean razorpayEnabled;

    private final String razorpayKeyId;

    private final String razorpayKeySecret;


    public PaymentService(

            @Value("${app.razorpay.enabled:false}")
            boolean razorpayEnabled,

            @Value("${app.razorpay.key-id:}")
            String razorpayKeyId,

            @Value("${app.razorpay.key-secret:}")
            String razorpayKeySecret

    ) {

        this.razorpayEnabled =
                razorpayEnabled;

        this.razorpayKeyId =
                razorpayKeyId;

        this.razorpayKeySecret =
                razorpayKeySecret;
    }


    /*
     * ============================================================
     * CREATE RAZORPAY ORDER
     * ============================================================
     */

    public String createOrder(
            Booking booking
    ) {

        ensureRazorpayEnabled();

        validateCredentials();


        if (booking == null) {

            throw new PaymentVerificationException(
                    "Booking is required"
            );
        }


        if (booking.getTotalAmount() == null) {

            throw new PaymentVerificationException(
                    "Booking amount is missing"
            );
        }


        if (booking.getTotalAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new PaymentVerificationException(
                    "Booking amount must be greater than zero"
            );
        }


        /*
         * Razorpay accepts the amount in the smallest
         * currency unit.
         *
         * Example:
         *
         * ₹1500.00
         *
         * becomes
         *
         * 150000 paise
         */

        long amountInSubunits =
                booking.getTotalAmount()
                        .movePointRight(2)
                        .longValueExact();


        try {

            RazorpayClient razorpayClient =
                    new RazorpayClient(
                            razorpayKeyId,
                            razorpayKeySecret
                    );


            JSONObject orderRequest =
                    new JSONObject();


            orderRequest.put(
                    "amount",
                    amountInSubunits
            );


            orderRequest.put(
                    "currency",
                    booking.getCurrency()
            );


            /*
             * Razorpay receipt must be unique enough
             * to identify our booking.
             */

            orderRequest.put(
                    "receipt",
                    booking.getBookingReference()
            );


            /*
             * Automatically capture the payment
             * after successful authorization.
             */

            orderRequest.put(
                    "payment_capture",
                    1
            );


            com.razorpay.Order razorpayOrder =
                    razorpayClient.orders.create(
                            orderRequest
                    );


            String orderId =
                    razorpayOrder.get("id");


            if (orderId == null
                    || orderId.isBlank()) {

                throw new PaymentVerificationException(
                        "Razorpay did not return an order ID"
                );
            }


            return orderId;

        } catch (RazorpayException exception) {

            throw new PaymentVerificationException(
                    "Unable to create Razorpay payment order",
                    exception
            );

        } catch (ArithmeticException exception) {

            throw new PaymentVerificationException(
                    "Booking amount is invalid for Razorpay",
                    exception
            );
        }
    }


    /*
     * ============================================================
     * VERIFY RAZORPAY SIGNATURE
     * ============================================================
     *
     * Razorpay signature is generated from:
     *
     * razorpay_order_id + "|" + razorpay_payment_id
     *
     * using HMAC-SHA256 with the Razorpay key secret.
     */

    public void verifySignature(

            String razorpayOrderId,

            String razorpayPaymentId,

            String razorpaySignature

    ) {

        ensureRazorpayEnabled();

        validateCredentials();


        if (razorpayOrderId == null
                || razorpayOrderId.isBlank()) {

            throw new PaymentVerificationException(
                    "Razorpay order ID is required"
            );
        }


        if (razorpayPaymentId == null
                || razorpayPaymentId.isBlank()) {

            throw new PaymentVerificationException(
                    "Razorpay payment ID is required"
            );
        }


        if (razorpaySignature == null
                || razorpaySignature.isBlank()) {

            throw new PaymentVerificationException(
                    "Razorpay signature is required"
            );
        }


        try {

            String payload =
                    razorpayOrderId
                            + "|"
                            + razorpayPaymentId;


            String generatedSignature =
                    generateHmacSha256(
                            payload,
                            razorpayKeySecret
                    );


            /*
             * Constant-time comparison helps avoid
             * timing-based signature attacks.
             */

            boolean valid =
                    MessageDigest.isEqual(
                            generatedSignature
                                    .getBytes(StandardCharsets.UTF_8),

                            razorpaySignature
                                    .getBytes(StandardCharsets.UTF_8)
                    );


            if (!valid) {

                throw new PaymentVerificationException(
                        "Invalid Razorpay payment signature"
                );
            }

        } catch (
                PaymentVerificationException exception
        ) {

            throw exception;

        } catch (Exception exception) {

            throw new PaymentVerificationException(
                    "Unable to verify Razorpay payment signature",
                    exception
            );
        }
    }


    /*
     * ============================================================
     * HMAC-SHA256
     * ============================================================
     */

    private String generateHmacSha256(

            String data,

            String secret

    ) throws Exception {

        Mac mac =
                Mac.getInstance(
                        "HmacSHA256"
                );


        SecretKeySpec secretKey =
                new SecretKeySpec(
                        secret.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        "HmacSHA256"
                );


        mac.init(secretKey);


        byte[] digest =
                mac.doFinal(
                        data.getBytes(
                                StandardCharsets.UTF_8
                        )
                );


        StringBuilder hex =
                new StringBuilder(
                        digest.length * 2
                );


        for (byte value : digest) {

            hex.append(
                    String.format(
                            "%02x",
                            value & 0xff
                    )
            );
        }


        return hex.toString();
    }


    /*
     * ============================================================
     * RAZORPAY KEY ID
     * ============================================================
     *
     * Safe to expose to the frontend.
     *
     * NEVER expose the key secret.
     */

    public String getKeyId() {

        ensureRazorpayEnabled();

        validateCredentials();

        return razorpayKeyId;
    }


    /*
     * ============================================================
     * ENABLED CHECK
     * ============================================================
     */

    private void ensureRazorpayEnabled() {

        if (!razorpayEnabled) {

            throw new PaymentVerificationException(
                    "Razorpay payments are currently disabled"
            );
        }
    }


    /*
     * ============================================================
     * CREDENTIAL VALIDATION
     * ============================================================
     */

    private void validateCredentials() {

        if (razorpayKeyId == null
                || razorpayKeyId.isBlank()) {

            throw new PaymentVerificationException(
                    "Razorpay key ID is not configured"
            );
        }


        if (razorpayKeySecret == null
                || razorpayKeySecret.isBlank()) {

            throw new PaymentVerificationException(
                    "Razorpay key secret is not configured"
            );
        }


        /*
         * Prevent accidentally running the backend
         * with the placeholder credentials.
         */

        if (razorpayKeyId.equals(
                "rzp_test_0000000000000"
        )) {

            throw new PaymentVerificationException(
                    "Razorpay test key ID has not been configured"
            );
        }


        if (razorpayKeySecret.equals(
                "CHANGE_THIS_TEST_SECRET"
        )) {

            throw new PaymentVerificationException(
                    "Razorpay test key secret has not been configured"
            );
        }
    }
}