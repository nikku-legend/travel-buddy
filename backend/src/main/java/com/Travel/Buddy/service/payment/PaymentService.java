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
        if (booking == null) {
            throw new PaymentVerificationException(
                        "Booking is required"
            );
        }
        return createOrder(
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getBookingReference()
        );
    }

    public String createOrder(
            BigDecimal amount,
            String currency,
            String receipt
    ) {
        ensureRazorpayEnabled();
        validateCredentials();
        if (amount == null) {
            throw new PaymentVerificationException(
                        "Payment amount is missing"
            );
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentVerificationException(
                        "Payment amount must be greater than zero"
            );
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new PaymentVerificationException(
                        "A valid three-letter currency code is required"
            );
        }
        if (receipt == null || receipt.isBlank()
                || receipt.length() > 40) {
            throw new PaymentVerificationException(
                        "A valid payment receipt is required"
            );
        }
        try {
            long amountInSubunits = amount
                        .movePointRight(2)
                        .longValueExact();
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
            orderRequest.put("currency", currency);
            orderRequest.put(
                        "receipt",
                        receipt
            );
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
                        "Payment amount is invalid for Razorpay",
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

    public void verifyCapturedPayment(
            String orderId,
            String paymentId,
            String signature,
            BigDecimal expectedAmount,
            String expectedCurrency
    ) {
        verifySignature(orderId, paymentId, signature);
        if (expectedAmount == null || expectedCurrency == null) {
            throw new PaymentVerificationException(
                    "Expected payment amount and currency are required"
            );
        }

        try {
            RazorpayClient client = new RazorpayClient(
                    razorpayKeyId, razorpayKeySecret
            );
            com.razorpay.Payment payment =
                    client.payments.fetch(paymentId);
            if (!orderId.equals(payment.get("order_id"))) {
                throw new PaymentVerificationException(
                        "Payment does not belong to this order"
                );
            }
            if (!"captured".equalsIgnoreCase(
                    String.valueOf(payment.get("status"))
            )) {
                throw new PaymentVerificationException(
                        "Razorpay payment has not been captured"
                );
            }

            long expectedMinorUnits = expectedAmount
                    .movePointRight(2)
                    .longValueExact();
            long actualMinorUnits = Long.parseLong(
                    String.valueOf(payment.get("amount"))
            );
            if (actualMinorUnits != expectedMinorUnits
                    || !expectedCurrency.equalsIgnoreCase(
                    String.valueOf(payment.get("currency"))
            )) {
                throw new PaymentVerificationException(
                        "Razorpay payment amount or currency does not match the checkout"
                );
            }
        } catch (PaymentVerificationException exception) {
            throw exception;
        } catch (RazorpayException | ArithmeticException
                 | NumberFormatException exception) {
            throw new PaymentVerificationException(
                    "Unable to verify captured Razorpay payment",
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