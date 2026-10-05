package com.Travel.Buddy.service.voucher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Signs and verifies the payload encoded into a voucher QR code.
 * (FR-24)
 *
 * <h2>Why a signed token and not a booking reference</h2>
 *
 * <p>A QR containing {@code TB-1234ABCD} would be forgeable: anyone
 * who guessed or scraped a booking reference could produce a
 * valid-looking code. So the QR carries a token whose signature
 * covers the voucher identity, and verification recomputes it.
 *
 * <h2>Wire format</h2>
 *
 * <pre>
 *   TBV1.&lt;base64url(payload)&gt;.&lt;base64url(hmac)&gt;
 *
 *   payload = bookingId : voucherCode : validUntilEpochDay : nonce
 * </pre>
 *
 * <p>The payload carries no guest data, so a photographed voucher
 * leaks nothing. Guest details are revealed only after the signature
 * verifies.
 *
 * <h2>Constant-time comparison</h2>
 *
 * <p>The signature is compared with
 * {@link MessageDigest#isEqual(byte[], byte[])} rather than
 * {@code equals}, because {@code String.equals} short-circuits on the
 * first differing byte and leaks how much of a forged signature was
 * correct.
 */
@Service
public class VoucherTokenService {

    private static final Logger log =
            LoggerFactory.getLogger(VoucherTokenService.class);

    private static final String VERSION = "TBV1";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final int NONCE_BYTES = 12;

    private final byte[] secret;

    private final SecureRandom secureRandom =
            new SecureRandom();

    public VoucherTokenService(
            @Value("${app.voucher.qr-secret:${JWT_SECRET:}}")
            String secret
    ) {
        if (secret == null
                || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {

            /*
             * Failing loudly is far better than signing every voucher
             * with a weak or empty key, which would make all of them
             * forgeable.
             */
            throw new IllegalStateException(
                    "app.voucher.qr-secret must be set to at least 32 bytes. "
                            + "Generate one with: openssl rand -base64 48"
            );
        }

        this.secret = secret.getBytes(StandardCharsets.UTF_8);

        log.info(
                "Voucher QR signing key loaded ({} bytes)",
                this.secret.length
        );
    }

    /**
     * Builds the signed token to encode into a voucher's QR code.
     */
    public String issue(
            Long bookingId,
            String voucherCode,
            LocalDate validUntil
    ) {
        String payload =
                bookingId + ":"
                        + voucherCode + ":"
                        + validUntil.toEpochDay() + ":"
                        + randomNonce();

        String encodedPayload = base64Url(
                payload.getBytes(StandardCharsets.UTF_8)
        );

        return VERSION
                + "." + encodedPayload
                + "." + base64Url(sign(encodedPayload));
    }

    /**
     * Checks a scanned token and returns what it proves.
     *
     * @throws VoucherTokenException if the token is malformed, was
     *                               not issued by this server, or has
     *                               been tampered with
     */
    public VerifiedToken verify(String token) {

        if (token == null || token.isBlank()) {

            throw new VoucherTokenException(
                    "No voucher code was supplied"
            );
        }

        String[] parts = token.trim().split("\\.");

        if (parts.length != 3
                || !VERSION.equals(parts[0])) {

            throw new VoucherTokenException(
                    "Unrecognised voucher format"
            );
        }

        byte[] expected = sign(parts[1]);
        byte[] supplied = decodeBase64Url(parts[2]);

        /*
         * Constant-time. A short-circuiting comparison would let an
         * attacker recover a valid signature byte by byte.
         */
        if (!MessageDigest.isEqual(expected, supplied)) {

            throw new VoucherTokenException(
                    "This voucher failed signature verification and is not valid"
            );
        }

        String payload = new String(
                decodeBase64Url(parts[1]),
                StandardCharsets.UTF_8
        );

        String[] fields = payload.split(":", 4);

        if (fields.length != 4) {

            throw new VoucherTokenException(
                    "This voucher is malformed"
            );
        }

        try {

            return new VerifiedToken(
                    Long.parseLong(fields[0]),
                    fields[1],
                    LocalDate.ofEpochDay(
                            Long.parseLong(fields[2])
                    ),
                    fields[3]
            );

        } catch (NumberFormatException exception) {

            throw new VoucherTokenException(
                    "This voucher is malformed"
            );
        }
    }

    private byte[] sign(String value) {

        try {

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);

            mac.init(
                    new SecretKeySpec(
                            secret,
                            HMAC_ALGORITHM
                    )
            );

            return mac.doFinal(
                    value.getBytes(StandardCharsets.UTF_8)
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to sign voucher token",
                    exception
            );
        }
    }

    private String randomNonce() {

        byte[] bytes = new byte[NONCE_BYTES];
        secureRandom.nextBytes(bytes);

        return base64Url(bytes);
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value);
    }

    private byte[] decodeBase64Url(String value) {

        try {

            return Base64.getUrlDecoder().decode(value);

        } catch (IllegalArgumentException exception) {

            throw new VoucherTokenException(
                    "This voucher is malformed"
            );
        }
    }

    /**
     * What a verified token proves.
     */
    public record VerifiedToken(
            Long bookingId,
            String voucherCode,
            LocalDate validUntil,
            String nonce
    ) {
    }
}