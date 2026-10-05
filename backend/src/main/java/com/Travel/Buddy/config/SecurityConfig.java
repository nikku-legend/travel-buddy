package com.Travel.Buddy.config;

import com.Travel.Buddy.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        this.jwtAuthenticationFilter =
                jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http

                /* =====================================================
                   CSRF
                ===================================================== */

                .csrf(csrf -> csrf.disable())


                /* =====================================================
                   CORS
                ===================================================== */

                .cors(cors -> {})


                /* =====================================================
                   SESSION MANAGEMENT
                ===================================================== */

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )


                /* =====================================================
                   AUTHORIZATION
                ===================================================== */

                .authorizeHttpRequests(auth -> auth


                        /* =================================================
                           PUBLIC AUTHENTICATION
                        ================================================= */

                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/error"
                        ).permitAll()


                        /* =================================================
                           PUBLIC TRAVEL DISCOVERY
                        ================================================= */

                        .requestMatchers(
                                "/api/v1/geo/**",
                                "/api/v1/destinations/**",
                                "/api/v1/stays/**",
                                "/api/v1/guides/**"
                        ).permitAll()

                        /*
                         * The partner type catalogue is public so the
                         * "Become a Partner" page can show what each
                         * partner type needs BEFORE the visitor logs in.
                         * Only descriptive metadata is exposed here.
                         */
                        .requestMatchers(
                                "/api/v1/partner/types",
                                "/api/v1/partner/documents/allowed-types"
                        ).permitAll()

                        /*
                         * =================================================
                         * HOME DISCOVERY
                         *
                         * SRS 2.3 section 2 makes Home the discovery
                         * surface: destinations, attractions, stays
                         * and experiences, with the Custom Trip
                         * Planner as its main action. Gating it
                         * behind a login would hide the platform
                         * from exactly the visitors it exists to
                         * attract.
                         *
                         * Everything it returns is public catalog
                         * data. The only per-user affordances the
                         * cards offer are save-to-bucket-list and
                         * add-to-trip, and those are separate
                         * authenticated endpoints the client
                         * calls afterwards.
                         * =================================================
                         */

                        .requestMatchers(
                                "/api/v1/home/**"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cabs/**"
                        ).permitAll()

                        .requestMatchers(
                                "/api/v1/bucket-list/**"
                        )
                        .hasRole("USER")

                        /*
                         * =================================================
                         * PUBLIC REVIEWS AND RATINGS
                         *
                         * A traveller deciding where to book must be
                         * able to read ratings without an account.
                         *
                         * The two-segment pattern below matches
                         * /reviews/{type}/{id} only, so the
                         * authenticated endpoints that share the
                         * prefix - /reviews/mine and
                         * /reviews/eligibility - stay protected.
                         * =================================================
                         */

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/reviews/*/*",
                                "/api/v1/reviews/*/*/summary"
                        )
                        .permitAll()
                        /* =================================================
                           SUPER ADMIN
                        ================================================= */

                        .requestMatchers(
                                "/api/v1/admin/**"
                        ).hasRole("SUPER_ADMIN")


                        /* =================================================
                           HOTEL PARTNER
                        ================================================= */

                        .requestMatchers(
                                "/api/v1/partner/hotel/**"
                        ).hasRole("HOTEL_PARTNER")


                        /* =================================================
                           GUIDE PARTNER
                        ================================================= */

                        .requestMatchers(
                                "/api/v1/partner/guide/**"
                        ).hasRole("GUIDE_PARTNER")


                        /* =================================================
                           CAB / TRANSPORT PARTNER
                        ================================================= */

                        .requestMatchers(
                                "/api/v1/partner/cab/**"
                        ).hasRole("CAB_PARTNER")


                        /* =================================================
                           PARTNER APPLICATIONS (APPLICANT SIDE)

                           Any signed-in account may apply, including an
                           existing traveler. Applying grants nothing:
                           the partner role is only written when an admin
                           approves, so this matcher deliberately does not
                           require a partner role.
                        ================================================= */

                        .requestMatchers(
                                "/api/v1/partner/applications/**"
                        ).authenticated()


                        /* =================================================
                           USER PROFILE
                        ================================================= */

                        .requestMatchers(
                                "/api/v1/user/me"
                        ).authenticated()


                        /* =================================================
                           CAB RIDE BOOKINGS (AUTHENTICATED)
                        ================================================= */

                        .requestMatchers(
                                "/api/v1/cabs/**"
                        ).authenticated()


                        /* =================================================
                           BOOKING CANCELLATION

                           IMPORTANT:

                           This matcher MUST come before the general
                           POST /api/v1/bookings/** matcher below.

                           Cancellation is protected by authentication
                           here, while the controller/service verifies
                           that the authenticated user actually owns
                           the booking.
                        ================================================= */

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/bookings/*/cancel"
                        ).authenticated()


                        /* =================================================
                           NORMAL BOOKING POST OPERATIONS

                           Creating bookings and payment operations
                           continue to require ROLE_USER.
                        ================================================= */

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/bookings/**"
                        ).hasRole("USER")


                        /* =================================================
                           ALL OTHER BOOKING OPERATIONS
                        ================================================= */

                        .requestMatchers(
                                "/api/v1/bookings/**"
                        ).authenticated()


                        /* =================================================
                           EVERYTHING ELSE
                        ================================================= */

                        .anyRequest().authenticated()
                )


                /* =====================================================
                   JWT AUTHENTICATION FILTER
                ===================================================== */

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
