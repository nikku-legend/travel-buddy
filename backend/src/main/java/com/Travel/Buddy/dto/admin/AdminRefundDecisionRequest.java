package com.Travel.Buddy.dto.admin;

import jakarta.validation.constraints.Size;

/**
 * The desk's ruling on one refund. (FR-33)
 *
 * <p>{@code approve} authorizes the refund; it does not disburse
 * money. A reason is mandatory for a rejection -- the traveller's
 * record shows it, and an unexplained "FAILED" is how support
 * tickets get written.
 */
public record AdminRefundDecisionRequest(

        boolean approve,

        @Size(max = 500, message = "The reason may not exceed 500 characters")
        String reason
) {
}