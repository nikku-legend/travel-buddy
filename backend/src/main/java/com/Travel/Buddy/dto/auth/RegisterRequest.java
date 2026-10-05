package com.Travel.Buddy.dto.auth;

import com.Travel.Buddy.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100,
                message = "Password must be between 8 and 100 characters")
        String password,

        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        String phoneNumber,

        Role role
) {
}
