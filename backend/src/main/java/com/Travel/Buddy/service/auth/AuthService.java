package com.Travel.Buddy.service.auth;

import com.Travel.Buddy.dto.auth.AuthResponse;
import com.Travel.Buddy.dto.auth.LoginRequest;
import com.Travel.Buddy.dto.auth.RefreshTokenRequest;
import com.Travel.Buddy.dto.auth.RegisterRequest;
import com.Travel.Buddy.entity.RefreshToken;
import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.security.JwtService;
import com.Travel.Buddy.service.partner.RoleService;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;

    private final JwtService jwtService;

    private final RefreshTokenService refreshTokenService;

    private final RoleService roleService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            RoleService roleService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.roleService = roleService;
    }

    @Transactional
    public AuthResponse register(
            RegisterRequest request
    ) {

        String email =
                request.email()
                        .trim()
                        .toLowerCase();

        if (userRepository.existsByEmail(email)) {

            throw new IllegalArgumentException(
                    "Email is already registered"
            );
        }

        User user = new User();

        user.setFullName(
                request.fullName().trim()
        );

        user.setEmail(email);

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.password()
                )
        );

        user.setPhoneNumber(
                request.phoneNumber()
        );

        /*
         * ============================================================
         * ROLE ON REGISTRATION
         *
         * Registration ALWAYS creates a traveler account.
         *
         * Partner roles are deliberately NOT requestable here. They are
         * granted only when an admin approves a partner application
         * (see PartnerApplicationService.decide), which requires KYC
         * documents and a recorded decision. Accepting a partner role
         * at signup would let anyone self-appoint as a verified
         * supplier and publish properties or accept rides unreviewed.
         * ============================================================
         */
        if (request.role() != null
                && request.role() != Role.ROLE_USER) {

            throw new IllegalArgumentException(
                    "Partner accounts cannot be created at registration. "
                            + "Register as a traveler, then use "
                            + "\"Become a Partner\" to submit an application "
                            + "for admin approval."
            );
        }

        user.setRole(Role.ROLE_USER);

        User savedUser =
                userRepository.save(user);

        /*
         * Mirror the baseline traveler role into the user_roles table so
         * the new account is immediately usable by the multi-role
         * authority model.
         */
        roleService.grantBaselineTravelerRole(savedUser);

        return createAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(
            LoginRequest request
    ) {

        String email =
                request.email()
                        .trim()
                        .toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email,
                        request.password()
                )
        );

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "User not found"
                                )
                        );

        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(
            RefreshTokenRequest request
    ) {

        RefreshToken refreshToken =
                refreshTokenService.validateToken(
                        request.refreshToken()
                );

        User user =
                refreshToken.getUser();

        /*
         * Refresh token rotation:
         * revoke old token and issue a new one.
         */
        refreshToken.revoke();

        String newRefreshToken =
                refreshTokenService
                        .createRefreshToken(user);

        String accessToken =
                jwtService.generateToken(user);

        return new AuthResponse(
                accessToken,
                newRefreshToken,
                "Bearer",
                jwtService.getExpirationMs(),
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                primaryRole(user),
                roleNamesOf(user)
        );
    }

    @Transactional
    public void logout(
            String refreshToken
    ) {

        refreshTokenService.revokeToken(
                refreshToken
        );
    }

    private AuthResponse createAuthResponse(
            User user
    ) {

        String accessToken =
                jwtService.generateToken(user);

        String refreshToken =
                refreshTokenService
                        .createRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getExpirationMs(),
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                primaryRole(user),
                roleNamesOf(user)
        );
    }

    private List<String> roleNamesOf(User user) {

        return roleService.rolesOf(user.getUserId())
                .stream()
                .map(Enum::name)
                .toList();
    }

    private String primaryRole(User user) {

        Set<Role> roles =
                roleService.rolesOf(user.getUserId());

        for (Role candidate : List.of(
                Role.ROLE_SUPER_ADMIN,
                Role.ROLE_HOTEL_PARTNER,
                Role.ROLE_GUIDE_PARTNER,
                Role.ROLE_CAB_PARTNER
        )) {

            if (roles.contains(candidate)) {
                return candidate.name();
            }
        }

        return Role.ROLE_USER.name();
    }
}
