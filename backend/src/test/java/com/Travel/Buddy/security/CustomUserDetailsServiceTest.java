package com.Travel.Buddy.security;

import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.RoleEntity;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.entity.UserRole;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.repository.RoleRepository;
import com.Travel.Buddy.repository.UserRoleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which authentication mechanism the application actually uses.
 *
 * <p>This mattered more than it looks. The configuration also
 * declared an explicit DaoAuthenticationProvider, which made Spring
 * Security warn that UserDetailsService beans "will not be used",
 * while the provider was in fact handed this class. It worked, but
 * two mechanisms were competing to configure one manager, and which
 * one won was decided by which beans happened to exist.
 *
 * <p>These tests pin the behaviour that made the simplification
 * safe: authorities come from user_roles, and a user can hold
 * several at once.
 */
@SpringBootTest
@ActiveProfiles("test")
class CustomUserDetailsServiceTest {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User traveller;

    @BeforeEach
    void setUp() {
        traveller = new User();
        traveller.setFullName("Auth Probe");
        traveller.setEmail(UUID.randomUUID() + "@tb.local");
        traveller.setPasswordHash("{noop}irrelevant");
        traveller = userRepository.save(traveller);
    }

    /**
     * The roles catalogue is populated by the V27 migration, which
     * does not run in this profile -- the schema here is built from
     * the entities. So a grant would have nowhere to point, and the
     * test would be asserting against a database that cannot exist
     * in production.
     *
     * <p>Created the same way RoleService self-heals a missing
     * entry, since that is behaviour the running system already
     * depends on.
     */
    private RoleEntity catalogue(Role role) {
        return roleRepository.findByRoleName(role)
                .orElseGet(() -> {
                    RoleEntity entry = new RoleEntity();
                    entry.setRoleName(role);
                    entry.setDescription(role.name() + " role");
                    return roleRepository.save(entry);
                });
    }

    @Test
    @DisplayName("a traveller with no partner roles gets exactly one")
    void singleRoleByDefault() {
        assertEquals(
                Set.of("ROLE_USER"),
                authoritiesOf(load()),
                "a plain account must not inherit a partner role"
        );
    }

    @Test
    @DisplayName("a user can hold a traveller and partner role at once")
    void multipleRolesComeFromUserRoles() {
        grant(Role.ROLE_HOTEL_PARTNER);

        assertEquals(
                Set.of("ROLE_USER", "ROLE_HOTEL_PARTNER"),
                authoritiesOf(load()),
                "multi-role is the whole point of user_roles: the "
                        + "legacy single column could not express it"
        );
    }

    @Test
    @DisplayName("three simultaneous roles are all issued")
    void threeRolesAtOnce() {
        grant(Role.ROLE_HOTEL_PARTNER);
        grant(Role.ROLE_GUIDE_PARTNER);
        grant(Role.ROLE_CAB_PARTNER);

        assertEquals(
                Set.of(
                        "ROLE_USER",
                        "ROLE_HOTEL_PARTNER",
                        "ROLE_GUIDE_PARTNER",
                        "ROLE_CAB_PARTNER"
                ),
                authoritiesOf(load())
        );
    }

    @Test
    @DisplayName("an account with no grant row falls back to its own role")
    void fallsBackToLegacyColumn() {
        assertEquals(
                Set.of("ROLE_USER"),
                authoritiesOf(load()),
                "an account must never end up with zero authorities, "
                        + "even if a deployment skipped the backfill"
        );
    }

    @Test
    @DisplayName("losing the traveller row never locks someone out")
    void travellerAccessIsUnconditional() {
        /*
         * The exact shape this guards: a partner role granted, and
         * no ROLE_USER row. Before the fix the account authenticated
         * and was then refused by every traveller endpoint, with
         * nothing to tell the user why.
         */
        grant(Role.ROLE_HOTEL_PARTNER);

        assertTrue(
                authoritiesOf(load()).contains("ROLE_USER"),
                "a partner grant must never cost someone access to "
                        + "their own bookings and trips"
        );
    }

    @Test
    @DisplayName("an unknown account is refused, not authenticated blank")
    void unknownUserIsRefused() {
        assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService
                        .loadUserByUsername("nobody@tb.local")
        );
    }

    @Test
    @DisplayName("the email is matched case-insensitively")
    void emailIsNormalised() {
        UserDetails upper = userDetailsService.loadUserByUsername(
                traveller.getEmail().toUpperCase()
        );

        assertTrue(
                upper.getUsername().equalsIgnoreCase(
                        traveller.getEmail()),
                "a login typed in a different case must still find "
                        + "the account"
        );
    }

    private void grant(Role role) {
        UserRole link = new UserRole();
        link.setUser(traveller);

        /*
         * user_roles points at the roles catalogue, not the enum,
         * so a grant has to resolve the catalogue row first. The
         * failure mode matters: silently inserting a link with a
         * null role would issue an account with fewer authorities
         * than the admin intended, and nothing would say so.
         */
        link.setRole(catalogue(role));

        userRoleRepository.save(link);
    }

    private UserDetails load() {
        return userDetailsService.loadUserByUsername(
                traveller.getEmail()
        );
    }

    private Set<String> authoritiesOf(UserDetails details) {
        return details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}