package com.Travel.Buddy.service.payment;

import com.Travel.Buddy.exception.PaymentVerificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Razorpay payment verification")
class PaymentServiceTest {

    private static final String SECRET = "local-test-secret";
    private final PaymentService paymentService =
            new PaymentService(true, "rzp_test_local", SECRET);

    @Test
    @DisplayName("accepts the signature for the exact order and payment")
    void acceptsValidSignature() {
        String orderId = "order_test_123";
        String paymentId = "pay_test_456";

        assertDoesNotThrow(() ->
                paymentService.verifySignature(
                        orderId,
                        paymentId,
                        signature(orderId, paymentId)
                )
        );
    }

    @Test
    @DisplayName("rejects a signature for a different payment")
    void rejectsSignatureForDifferentPayment() {
        String signature = signature(
                "order_test_123",
                "pay_test_expected"
        );

        assertThrows(
                PaymentVerificationException.class,
                () -> paymentService.verifySignature(
                        "order_test_123",
                        "pay_test_attacker",
                        signature
                )
        );
    }

    @Test
    @DisplayName("requires the expected amount and currency")
    void requiresExpectedAmountAndCurrency() {
        String orderId = "order_test_123";
        String paymentId = "pay_test_456";

        assertThrows(
                PaymentVerificationException.class,
                () -> paymentService.verifyCapturedPayment(
                        orderId,
                        paymentId,
                        signature(orderId, paymentId),
                        null,
                        "INR"
                )
        );
    }

    @Test
    @DisplayName("refuses verification when the gateway is disabled")
    void refusesWhenGatewayIsDisabled() {
        PaymentService disabled =
                new PaymentService(false, "rzp_test_local", SECRET);

        assertThrows(
                PaymentVerificationException.class,
                () -> disabled.verifySignature(
                        "order_test_123",
                        "pay_test_456",
                        "signature"
                )
        );
    }

    private static String signature(
            String orderId,
            String paymentId
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    SECRET.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            byte[] digest = mac.doFinal(
                    (orderId + "|" + paymentId)
                            .getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to create test signature",
                    exception
            );
        }
    }
}
