package com.Travel.Buddy.dto.dispute;

import com.Travel.Buddy.entity.DisputeStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Moves a dispute to a new working state. Refused on terminal
 * disputes by the service.
 */
public record ChangeDisputeStatusRequest(

        @NotNull
        DisputeStatus status,

        String note
) {
}