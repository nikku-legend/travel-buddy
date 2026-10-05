package com.Travel.Buddy.dto.dispute;

import com.Travel.Buddy.entity.DisputeResolution;
import com.Travel.Buddy.entity.DisputeStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ResolveDisputeRequest(

        /*
         * Nullable so a moderator can close a claim as REJECTED
         * with a written reason and no ruling attached.
         */
        DisputeResolution resolution,

        /*
         * Required only for the rulings that move money. The
         * service enforces that, because a conditional
         * @NotNull cannot express "required for some values of
         * another field".
         */
        BigDecimal resolvedAmount,

        @Size(
                max = 4000,
                message = "Notes must be at most 4000 characters"
        )
        String notes,

        /*
         * Optional: a moderator can close a dispute as rejected
         * instead of resolving it. Defaults to resolving.
         */
        Boolean reject
) {
    public boolean isRejection() {
        return Boolean.TRUE.equals(reject);
    }
}