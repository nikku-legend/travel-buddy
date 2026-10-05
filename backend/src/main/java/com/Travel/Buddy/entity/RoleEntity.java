package com.Travel.Buddy.entity;

/**
 * Maps the {@code roles} catalogue table.
 *
 * <p>Named {@code RoleEntity} rather than {@code Role} so it does not
 * collide with the {@link Role} enum, which remains the type-safe
 * representation of a role name in Java code.
 */
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "roles",
        indexes = {
                @Index(
                        name = "idx_roles_name",
                        columnList = "role_name"
                )
        }
)
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "role_name",
            nullable = false,
            unique = true,
            length = 30
    )
    private Role roleName;

    @Column(name = "description", length = 150)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Role getRoleName() {
        return roleName;
    }

    public void setRoleName(Role roleName) {
        this.roleName = roleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
