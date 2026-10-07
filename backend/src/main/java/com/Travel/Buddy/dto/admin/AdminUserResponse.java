package com.Travel.Buddy.dto.admin;

import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.UserStatus;

import java.time.LocalDateTime;

/**
 * A user as the management console shows them. (FR-31)
 */
public record AdminUserResponse(

        Long userId,

        String fullName,

        String email,

        String phoneNumber,

        Role role,

        UserStatus status,

        long bookingCount,

        LocalDateTime createdAt
) {
}