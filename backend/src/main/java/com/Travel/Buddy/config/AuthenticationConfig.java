package com.Travel.Buddy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

/**
 * The one authentication mechanism the application uses.
 *
 * <p>There used to be a second bean here: an explicitly constructed
 * {@code DaoAuthenticationProvider} built from the
 * {@code UserDetailsService} and {@code PasswordEncoder} beans. Spring
 * Security logged a warning on every boot saying so:
 *
 * <pre>
 * Global AuthenticationManager configured with an
 * AuthenticationProvider bean. UserDetailsService beans will not be
 * used by Spring Security for automatically configuring
 * username/password login.
 * </pre>
 *
 * <p>It worked, because the provider was handed our
 * CustomUserDetailsService -- which is the class that reads
 * authorities from {@code user_roles} rather than the legacy single
 * role column. So multi-role access was not broken.
 *
 * <p>It was still wrong. Two mechanisms competed to configure the
 * same manager, and which one won was decided by which beans
 * happened to exist. Adding a second {@code UserDetailsService}
 * anywhere in the application would have changed which
 * implementation authenticated every login in the platform, with
 * nothing to mark the change. A security control whose behaviour
 * depends on bean ordering is not a control.
 *
 * <p>Declaring only the {@code UserDetailsService} and
 * {@code PasswordEncoder} beans is the supported path: Spring wires
 * them into the global manager itself, so there is exactly one
 * mechanism and it is the obvious one.
 *
 * <p>The manager is still exposed as a bean because AuthService
 * authenticates with it directly.
 */
@Configuration
public class AuthenticationConfig {

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {

        return configuration.getAuthenticationManager();
    }
}