package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    Optional<RoleEntity> findByRoleName(Role roleName);

    boolean existsByRoleName(Role roleName);
}
