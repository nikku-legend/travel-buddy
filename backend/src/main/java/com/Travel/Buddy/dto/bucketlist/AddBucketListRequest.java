package com.Travel.Buddy.dto.bucketlist;

import jakarta.validation.constraints.NotNull;

public record AddBucketListRequest(

        @NotNull(message = "Place ID is required")
        Long placeId

) {
}