package com.Travel.Buddy.service.auth;

import com.Travel.Buddy.dto.auth.LoginRequest;
import com.Travel.Buddy.dto.auth.RefreshTokenRequest;
import com.Travel.Buddy.entity.RefreshToken;
import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.entity.UserStatus;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.security.JwtService;
import com.Travel.Buddy.service.partner.RoleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Suspension must bite where sessions begin (FR-31): a suspended
 * account can neither log in nor refresh, so suspension is more
 * than a label on the row.
 */
class AuthServiceSuspensionTest {

    private final UserRepository userRepository =
            mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder =
            mock(PasswordEncoder.class);
    private final AuthenticationManager authenticationManager =
            mock(AuthenticationManager.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final RefreshTokenService refreshTokenService =
            mock(RefreshTokenService.class);
    private final RoleService roleService = mock(RoleService.class);

    private AuthService authService;
    private User suspended;
    private User active;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                authenticationManager,
                jwtService,
                refreshTokenService,
                roleService
        );

        suspended = new User();
        suspended.setUserId(5L);
        suspended.setFullName("Blocked Person");
        suspended.setEmail("blocked@example.com");
        suspended.setStatus(UserStatus.SUSPENDED);

        active = new User();
        active.setUserId(6L);
        active.setFullName("Travelling Person");
        active.setEmail("active@example.com");
        active.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void loginRejectsASuspendedAccount() {
        when(userRepository.findByEmail("blocked@example.com"))
                .thenReturn(Optional.of(suspended));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(
                        new LoginRequest(
                                "blocked@example.com",
                                "password"
                        )
                )
        );

        assertTrue(
                exception.getMessage().toLowerCase()
                        .contains("suspended")
        );
        verify(refreshTokenService, never())
                .createRefreshToken(any(User.class));
    }

    @Test
    void refreshRejectsASuspendedAccountWithoutRotatingTheToken() {
        RefreshToken token = mock(RefreshToken.class);
        when(token.getUser()).thenReturn(suspended);
        when(refreshTokenService.validateToken("refresh-1"))
                .thenReturn(token);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.refresh(
                        new RefreshTokenRequest("refresh-1")
                )
        );

        assertTrue(
                exception.getMessage().toLowerCase()
                        .contains("suspended")
        );
        verify(token, never()).revoke();
        verify(refreshTokenService, never())
                .createRefreshToken(any(User.class));
    }

    @Test
    void anActiveAccountStillLogsInAndReceivesTokens() {
        when(userRepository.findByEmail("active@example.com"))
                .thenReturn(Optional.of(active));
        when(roleService.rolesOf(anyLong()))
                .thenReturn(Set.of(Role.ROLE_USER));
        when(jwtService.generateToken(active))
                .thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(active))
                .thenReturn("refresh-token");

        var response = authService.login(
                new LoginRequest("active@example.com", "password")
        );

        assertNotNull(response);
        assertNotNull(response.accessToken());
        assertTrue("access-token".equals(response.accessToken()));
        verify(refreshTokenService).createRefreshToken(active);
    }
}
