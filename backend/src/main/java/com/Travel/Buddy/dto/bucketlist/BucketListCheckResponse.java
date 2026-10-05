package com.Travel.Buddy.dto.bucketlist;

public record BucketListCheckResponse(
        Long placeId,
        boolean saved,
        Long bucketId
) {
}