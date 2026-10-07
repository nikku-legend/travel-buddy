package com.Travel.Buddy.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsConfigTest {

    @Test
    void usesOnlyConfiguredOrigins() {
        CorsConfig config = new CorsConfig(
                "https://travel.example, https://admin.example"
        );
        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest());

        assertEquals(
                java.util.List.of(
                        "https://travel.example",
                        "https://admin.example"
                ),
                cors.getAllowedOrigins()
        );
        assertEquals(Boolean.TRUE, cors.getAllowCredentials());
    }

    @Test
    void refusesWildcardOriginWithCredentialsEnabled() {
        CorsConfig config = new CorsConfig("*");

        assertThrows(
                IllegalStateException.class,
                config::corsConfigurationSource
        );
    }
}
