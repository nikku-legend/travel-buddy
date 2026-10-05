package com.Travel.Buddy.dto.bucketlist;

import java.time.LocalDateTime;

public record BucketListResponse(
        Long bucketId,
        Long placeId,
        String placeName,
        String description,
        String imageUrl,
        Long stateId,
        LocalDateTime addedAt
) {
}