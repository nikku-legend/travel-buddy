package com.Travel.Buddy.service.partner;

import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.RoleEntity;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.entity.UserRole;
import com.Travel.Buddy.repository.RoleRepository;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.repository.UserRoleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Owns the many-to-many role model.
 *
 * <p>Two invariants this service exists to protect:
 *
 * <ol>
 *   <li>A user always keeps {@code ROLE_USER}. Partner roles are
 *       <em>additional</em> to traveler access, never a replacement,
 *       so an approved partner can still plan and book their own trips.</li>
 *   <li>{@code ROLE_SUPER_ADMIN} can never be granted or revoked
 *       through the partner approval flow, only by another admin.</li>
 * </ol>
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;

    private final UserRoleRepository userRoleRepository;

    private final UserRepository userRepository;

    public RoleService(
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            UserRepository userRepository
    ) {
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
    }

    /**
     * Every role currently granted to a user.
     */
    @Transactional(readOnly = true)
    public Set<Role> rolesOf(Long userId) {
        return Set.copyOf(
                userRoleRepository
                        .findRoleNamesByUserId(userId)
        );
    }

    @Transactional(readOnly = true)
    public boolean hasRole(
            Long userId,
            Role role
    ) {
        return userRoleRepository
                .existsByUser_UserIdAndRole_RoleName(
                        userId,
                        role
                );
    }

    /**
     * Grants a role if it is not already held.
     *
     * @return {@code true} when a new grant was created
     */
    @Transactional
    public boolean grant(
            User user,
            Role role,
            User grantedBy
    ) {

        if (user == null || role == null) {
            return false;
        }

        if (userRoleRepository
                .existsByUser_UserIdAndRole_RoleName(
                        user.getUserId(),
                        role
                )) {

            return false;
        }

        userRoleRepository.save(
                buildUserRole(
                        user,
                        role,
                        grantedBy
                )
        );

        return true;
    }

    /**
     * Ensures the {@code roles} catalogue contains the given role,
     * inserting it if missing.
     *
     * <p>The catalogue is normally seeded by migration V27. Re-inserting
     * a missing row here makes role granting idempotent and keeps the
     * grant path working in environments where Flyway has not run, such
     * as tests that build their schema from the JPA entities.
     */
    @Transactional
    public RoleEntity ensureCatalogueEntry(
            Role role
    ) {

        return roleRepository
                .findByRoleName(role)
                .orElseGet(() -> {

                    RoleEntity entity = new RoleEntity();

                    entity.setRoleName(role);
                    entity.setDescription(
                            "Auto-inserted: " + role.name()
                    );

                    return roleRepository.save(entity);
                });
    }

    /**
     * Inserts every role that is missing, so the catalogue is always
     * complete before the first grant.
     */
    @Transactional
    public void ensureCatalogue() {

        for (Role role : Role.values()) {

            if (role == Role.ROLE_GUEST) {
                continue;
            }

            ensureCatalogueEntry(role);
        }
    }

    /**
     * Revokes a role. Refuses to remove traveler access or the last
     * super administrator, because either would lock accounts out of
     * the platform permanently.
     */
    @Transactional
    public void revoke(
            Long userId,
            Role role
    ) {

        if (role == Role.ROLE_USER) {
            throw new IllegalArgumentException(
                    "Traveler access cannot be revoked"
            );
        }

        if (role == Role.ROLE_SUPER_ADMIN) {
            throw new IllegalArgumentException(
                    "Super administrator role cannot be revoked through this flow"
            );
        }

        userRoleRepository
                .deleteByUser_UserIdAndRole_RoleName(
                        userId,
                        role
                );
    }

    /**
     * Called immediately after a user registers. Every account starts
     * as a traveler; partner roles can only arrive through admin
     * approval of a partner application.
     */
    @Transactional
    public void grantBaselineTravelerRole(User user) {

        grant(user, Role.ROLE_USER, null);
    }

    private UserRole buildUserRole(
            User user,
            Role role,
            User grantedBy
    ) {

        /*
         * Self-heal rather than fail: a missing catalogue row would
         * otherwise make every grant throw, which is a far worse
         * failure mode than an inserted reference row.
         */
        RoleEntity roleEntity =
                ensureCatalogueEntry(role);

        UserRole userRole = new UserRole();

        userRole.setUser(user);
        userRole.setRole(roleEntity);
        userRole.setGrantedBy(grantedBy);

        return userRole;
    }

    /**
     * All catalogue roles, used by the admin user-management screen.
     */
    @Transactional(readOnly = true)
    public List<RoleEntity> findAllRoles() {
        return roleRepository.findAll();
    }
}
