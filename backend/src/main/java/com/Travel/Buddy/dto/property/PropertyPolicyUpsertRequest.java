package com.Travel.Buddy.dto.property;

import com.Travel.Buddy.entity.PolicyType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A house policy. (SRS 2.3 section 6.2)
 */
public record PropertyPolicyUpsertRequest(

        @NotNull
        PolicyType policyType,

        @NotBlank
        @Size(max = 120)
        String title,

        @Size(max = 1000)
        String description,

        Integer sortOrder
) {
}