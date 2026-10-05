package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Join entity between {@link User} and {@link RoleEntity}.
 *
 * <p>Represents "this user holds this role, granted at this time,
 * approved by this admin". That is what makes multi-role accounts
 * possible - a user can simultaneously be a traveler, a hotel
 * partner and a cab partner.
 */
@Entity
@Table(
        name = "user_roles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_roles_user_role",
                        columnNames = {"user_id", "role_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_user_roles_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_user_roles_role",
                        columnList = "role_id"
                )
        }
)
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_role_id")
    private Long userRoleId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_user_roles_user"
            )
    )
    private User user;

    @ManyToOne(
            fetch = FetchType.EAGER,
            optional = false
    )
    @JoinColumn(
            name = "role_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_user_roles_role"
            )
    )
    private RoleEntity role;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    /**
     * The admin who approved the grant, or {@code null} for baseline
     * roles that are granted automatically (every user is a traveler).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "granted_by_user_id",
            foreignKey = @ForeignKey(
                    name = "fk_user_roles_granted_by"
            )
    )
    private User grantedBy;

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
    }

    public Long getUserRoleId() {
        return userRoleId;
    }

    public void setUserRoleId(Long userRoleId) {
        this.userRoleId = userRoleId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public RoleEntity getRole() {
        return role;
    }

    public void setRole(RoleEntity role) {
        this.role = role;
    }

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }

    public User getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(User grantedBy) {
        this.grantedBy = grantedBy;
    }
}
