package com.Travel.Buddy.service.payment;

import com.Travel.Buddy.dto.payment.PaymentOrderResponse;
import com.Travel.Buddy.entity.Booking;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

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

        this.razorpayEnabled = razorpayEnabled;
        this.razorpayKeyId = razorpayKeyId;
        this.razorpayKeySecret = razorpayKeySecret;
    }

    /**
     * Create a Razorpay order for the supplied booking.
     *
     * Razorpay expects the amount in the smallest currency unit.
     *
     * Example:
     *
     * ₹1,250.00
     *
     * becomes:
     *
     * 125000 paise
     */
    public PaymentOrderResponse createOrder(
            Booking booking
    ) {

        ensureRazorpayEnabled();

        validateCredentials();

        if (booking == null) {
            throw new IllegalArgumentException(
                    "Booking is required"
            );
        }

        if (booking.getTotalAmount() == null) {
            throw new IllegalArgumentException(
                    "Booking amount is required"
            );
        }

        if (booking.getCurrency() == null
                || booking.getCurrency().isBlank()) {

            throw new IllegalArgumentException(
                    "Booking currency is required"
            );
        }

        try {

            RazorpayClient razorpayClient =
                    new RazorpayClient(
                            razorpayKeyId,
                            razorpayKeySecret
                    );

            long amountInPaise =
                    booking.getTotalAmount()
                            .movePointRight(2)
                            .setScale(
                                    0,
                                    RoundingMode.HALF_UP
                            )
                            .longValueExact();

            if (amountInPaise <= 0) {
                throw new IllegalArgumentException(
                        "Payment amount must be greater than zero"
                );
            }

            JSONObject options =
                    new JSONObject();

            options.put(
                    "amount",
                    amountInPaise
            );

            options.put(
                    "currency",
                    booking.getCurrency()
                            .trim()
                            .toUpperCase()
            );

            options.put(
                    "receipt",
                    booking.getBookingReference()
            );

            options.put(
                    "payment_capture",
                    1
            );

            Order razorpayOrder =
                    razorpayClient.orders.create(
                            options
                    );

            String orderId =
                    razorpayOrder.get("id");

            return new PaymentOrderResponse(

                    orderId,

                    booking.getTotalAmount(),

                    booking.getCurrency()
                            .trim()
                            .toUpperCase(),

                    razorpayKeyId,

                    booking.getBookingId(),

                    booking.getBookingReference()
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to create Razorpay payment order",
                    exception
            );
        }
    }

    /**
     * Verify the Razorpay payment signature.
     *
     * Razorpay signs:
     *
     * razorpay_order_id + "|" + razorpay_payment_id
     *
     * using the Razorpay key secret and HMAC-SHA256.
     *
     * The generated hexadecimal signature must exactly match
     * the signature received from Razorpay Checkout.
     */
    public void verifySignature(

            String orderId,

            String paymentId,

            String signature
    ) {

        ensureRazorpayEnabled();

        validateCredentials();

        if (orderId == null
                || orderId.isBlank()) {

            throw new IllegalArgumentException(
                    "Razorpay order ID is required"
            );
        }

        if (paymentId == null
                || paymentId.isBlank()) {

            throw new IllegalArgumentException(
                    "Razorpay payment ID is required"
            );
        }

        if (signature == null
                || signature.isBlank()) {

            throw new IllegalArgumentException(
                    "Razorpay signature is required"
            );
        }

        try {

            String payload =
                    orderId.trim()
                            + "|"
                            + paymentId.trim();

            String generatedSignature =
                    generateHmacSha256(
                            payload,
                            razorpayKeySecret
                    );

            if (!constantTimeEquals(
                    generatedSignature,
                    signature.trim()
            )) {

                throw new IllegalArgumentException(
                        "Invalid Razorpay payment signature"
                );
            }

        } catch (IllegalArgumentException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to verify Razorpay payment signature",
                    exception
            );
        }
    }

    /**
     * Generate HMAC-SHA256 signature.
     */
    private String generateHmacSha256(

            String payload,

            String secret
    ) throws Exception {

        Mac mac =
                Mac.getInstance("HmacSHA256");

        SecretKeySpec secretKey =
                new SecretKeySpec(
                        secret.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        "HmacSHA256"
                );

        mac.init(secretKey);

        byte[] hash =
                mac.doFinal(
                        payload.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        StringBuilder hexadecimal =
                new StringBuilder(
                        hash.length * 2
                );

        for (byte value : hash) {

            hexadecimal.append(
                    String.format(
                            "%02x",
                            value & 0xff
                    )
            );
        }

        return hexadecimal.toString();
    }

    /**
     * Constant-time comparison prevents timing-based
     * comparison weaknesses.
     */
    private boolean constantTimeEquals(

            String expected,

            String actual
    ) {

        byte[] expectedBytes =
                expected.getBytes(
                        StandardCharsets.UTF_8
                );

        byte[] actualBytes =
                actual.getBytes(
                        StandardCharsets.UTF_8
                );

        if (expectedBytes.length
                != actualBytes.length) {

            return false;
        }

        int difference = 0;

        for (int index = 0;
             index < expectedBytes.length;
             index++) {

            difference |=
                    expectedBytes[index]
                            ^ actualBytes[index];
        }

        return difference == 0;
    }

    /**
     * Make sure Razorpay has explicitly been enabled.
     */
    private void ensureRazorpayEnabled() {

        if (!razorpayEnabled) {

            throw new IllegalStateException(
                    "Razorpay payment gateway is disabled"
            );
        }
    }

    /**
     * Make sure real Razorpay credentials are available.
     */
    private void validateCredentials() {

        if (razorpayKeyId == null
                || razorpayKeyId.isBlank()
                || razorpayKeySecret == null
                || razorpayKeySecret.isBlank()) {

            throw new IllegalStateException(
                    "Razorpay credentials are not configured"
            );
        }

        if (razorpayKeyId.equals(
                "rzp_test_0000000000000"
        )
                || razorpayKeySecret.equals(
                "CHANGE_THIS_TEST_SECRET"
        )) {

            throw new IllegalStateException(
                    "Real Razorpay test credentials are required"
            );
        }
    }
}