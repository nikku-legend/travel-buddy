package com.Travel.Buddy.dto.property;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A property image. (SRS 2.3 section 6.2)
 *
 * <p>{@code cover} designates the image the page leads with, so at
 * most one may be set; the service enforces that rather than
 * trusting the caller to manage the invariant.
 */
public record PropertyImageUpsertRequest(

        @NotBlank
        @Size(max = 500)
        String imageUrl,

        @Size(max = 200)
        String altText,

        Integer sortOrder,

        Boolean cover
) {
}