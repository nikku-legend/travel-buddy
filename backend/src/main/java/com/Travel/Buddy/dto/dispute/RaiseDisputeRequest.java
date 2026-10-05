package com.Travel.Buddy.dto.dispute;

import com.Travel.Buddy.entity.DisputeCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RaiseDisputeRequest(

        @NotNull
        DisputeCategory category,

        @NotBlank
        @Size(
                min = 5,
                max = 200,
                message = "Subject must be between 5 and 200 characters"
        )
        String subject,

        @NotBlank
        @Size(
                min = 20,
                max = 4000,
                message = "Please describe what happened in at least 20 characters"
        )
        String description,

        @NotNull
        @DecimalMin(
                value = "0.00",
                message = "A claim cannot ask for a negative amount"
        )
        @Digits(
                integer = 10,
                fraction = 2,
                message = "Amounts allow at most two decimal places"
        )
        BigDecimal requestedAmount
) {
}