package com.Travel.Buddy.security;

import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.repository.UserRoleRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads an account and everything it is allowed to do. (FR-01)
 *
 * <p>This is the single place authentication reads authorities
 * from. The configuration used to also declare an explicit
 * DaoAuthenticationProvider, which made Spring Security warn that
 * UserDetailsService beans would not be used; the provider was in
 * fact handed this class, so it worked, but two mechanisms were
 * competing to configure one manager and which won was decided by
 * which beans happened to exist. Only the UserDetailsService and
 * PasswordEncoder beans are declared now, so this is the only
 * implementation.
 */
@Service
public class CustomUserDetailsService
        implements UserDetailsService {

    private final UserRepository userRepository;

    private final UserRoleRepository userRoleRepository;

    public CustomUserDetailsService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findByEmail(
                email.toLowerCase().trim()
        ).orElseThrow(() ->
                new UsernameNotFoundException(
                        "User not found"
                )
        );

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                authoritiesFor(user)
        );
    }

    /**
     * Authorities come from user_roles, not the legacy single-role
     * column, so one account can be a traveller and an approved
     * partner at the same time.
     */
    private List<SimpleGrantedAuthority> authoritiesFor(
            User user
    ) {
        Set<Role> roles = new LinkedHashSet<>(
                userRoleRepository.findRoleNamesByUserId(
                        user.getUserId()
                )
        );

        if (roles.isEmpty()) {
            /*
             * Fall back to the legacy column so an account is never
             * left with no authorities at all if a deployment
             * skipped the V27 backfill.
             */
            roles.add(user.getRole());
        }

        /*
         * Traveller access is unconditional.
         *
         * <p>RoleService refuses to revoke ROLE_USER, so this should
         * be unreachable. It is enforced here as well because the
         * consequence of it being reachable is severe and silent:
         * an account that kept its partner role but lost its
         * ROLE_USER row would authenticate normally, be refused by
         * every traveller endpoint, and be unable to see its own
         * bookings with nothing to explain why.
         *
         * <p>Whoever grants a partner role must not be able to
         * lock a person out of their own account by accident.
         */
        roles.add(Role.ROLE_USER);

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(
                        role.name()
                ))
                .toList();
    }
}