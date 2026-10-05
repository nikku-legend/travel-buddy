package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository
        extends JpaRepository<UserRole, Long> {

    /**
     * All role names currently granted to a user.
     *
     * <p>Used to build the Spring Security authority list. Executed
     * directly against {@code user_roles} so it stays correct even when
     * the {@code User.userRoles} collection is not initialised
     * (open-in-view is disabled in this project).
     */
    @Query("""
            SELECT userRole.role.roleName
            FROM UserRole userRole
            WHERE userRole.user.userId = :userId
            """)
    List<Role> findRoleNamesByUserId(
            @Param("userId") Long userId
    );

    @Query("""
            SELECT userRole
            FROM UserRole userRole
                JOIN FETCH userRole.role
            WHERE userRole.user.userId = :userId
            """)
    List<UserRole> findByUserIdWithRole(
            @Param("userId") Long userId
    );

    boolean existsByUser_UserIdAndRole_RoleName(
            Long userId,
            Role roleName
    );

    void deleteByUser_UserIdAndRole_RoleName(
            Long userId,
            Role roleName
    );
}
