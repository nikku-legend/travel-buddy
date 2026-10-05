package com.Travel.Buddy.dto.cancellation;

import com.Travel.Buddy.entity.CancellationReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CancelBookingRequest(

        @NotNull(message = "Cancellation reason is required")
        CancellationReason reason,

        @Size(
                max = 1000,
                message = "Cancellation note cannot exceed 1000 characters"
        )
        String note

) {
}