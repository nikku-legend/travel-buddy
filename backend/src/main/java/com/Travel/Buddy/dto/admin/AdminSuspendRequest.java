package com.Travel.Buddy.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Why the account is being suspended. (FR-31)
 *
 * <p>A blank reason is refused rather than defaulted: "suspended
 * for no recorded reason" is exactly the kind of row the audit
 * log exists to prevent.
 */
public record AdminSuspendRequest(

        @NotBlank(message = "A reason is required to suspend an account")
        @Size(max = 500, message = "The reason may not exceed 500 characters")
        String reason
) {
}