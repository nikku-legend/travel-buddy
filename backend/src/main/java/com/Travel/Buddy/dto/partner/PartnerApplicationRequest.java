package com.Travel.Buddy.dto.partner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Applicant-submitted partner information.
 *
 * <p>Used to create a draft and to edit a draft, a rejected
 * application, or one pending correction.
 */
public record PartnerApplicationRequest(

        @NotBlank(message = "Business or service name is required")
        @Size(max = 150, message = "Business name must not exceed 150 characters")
        String businessName,

        @Size(max = 20, message = "Contact phone must not exceed 20 characters")
        String contactPhone,

        @Size(max = 255, message = "Address must not exceed 255 characters")
        String addressLine,

        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "State must not exceed 100 characters")
        String state,

        @Size(max = 20, message = "Postal code must not exceed 20 characters")
        String postalCode,

        @Size(max = 50, message = "Tax identifier must not exceed 50 characters")
        String taxIdentifier,

        @Size(max = 1000, message = "Additional information must not exceed 1000 characters")
        String additionalInfo,

        @Size(max = 150, message = "Bank account name must not exceed 150 characters")
        String bankAccountName,

        @Size(max = 50, message = "Bank account number must not exceed 50 characters")
        String bankAccountNumber,

        @Size(max = 20, message = "IFSC must not exceed 20 characters")
        String bankIfsc
) {
}