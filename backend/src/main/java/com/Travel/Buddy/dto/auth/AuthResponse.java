package com.Travel.Buddy.dto.auth;

import java.util.List;

public record AuthResponse(

        String accessToken,

        String refreshToken,

        String tokenType,

        long expiresIn,

        Long userId,

        String fullName,

        String email,

        /**
         * Most privileged role held, retained for clients that read a
         * single role. Prefer {@link #roles}.
         */
        String role,

        /**
         * Every role the account holds, for example
         * {@code [ROLE_USER, ROLE_HOTEL_PARTNER]}.
         *
         * <p>Returned at login so the frontend can render the correct
         * portal entries without a second round trip.
         */
        List<String> roles
) {
}