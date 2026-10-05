package com.Travel.Buddy.controller;

import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.partner.RoleService;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserRepository userRepository;

    private final RoleService roleService;

    public UserController(
            UserRepository userRepository,
            RoleService roleService
    ) {
        this.userRepository = userRepository;
        this.roleService = roleService;
    }

    @GetMapping("/me")
    public UserProfileResponse me(
            Authentication authentication
    ) {

        User user =
                userRepository.findByEmail(
                        authentication.getName()
                ).orElseThrow();

        Set<String> roles = rolesOf(user.getUserId());

        return new UserProfileResponse(
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),

                /*
                 * `role` is retained for older clients that read a
                 * single role. It reports the most privileged role the
                 * account holds, so a legacy client does not fall back
                 * to the traveler view for an approved partner.
                 */
                primaryRole(roles),
                List.copyOf(roles)
        );
    }

    private Set<String> rolesOf(Long userId) {

        Set<String> roles = new LinkedHashSet<>();

        for (com.Travel.Buddy.entity.Role role :
                roleService.rolesOf(userId)) {

            roles.add(role.name());
        }

        return roles;
    }

    private String primaryRole(Set<String> roles) {

        for (String candidate : List.of(
                "ROLE_SUPER_ADMIN",
                "ROLE_HOTEL_PARTNER",
                "ROLE_GUIDE_PARTNER",
                "ROLE_CAB_PARTNER"
        )) {

            if (roles.contains(candidate)) {
                return candidate;
            }
        }

        return "ROLE_USER";
    }

    public record UserProfileResponse(
            Long userId,
            String fullName,
            String email,
            String phoneNumber,
            String role,
            List<String> roles
    ) {
    }
}