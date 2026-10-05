package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.BucketList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BucketListRepository
        extends JpaRepository<BucketList, Long> {

    /**
     * Get all bucket-list entries belonging to a user.
     */
    List<BucketList> findByUser_UserIdOrderByAddedAtDesc(
            Long userId
    );

    /**
     * Check whether a tourist place is already
     * present in the user's bucket list.
     */
    boolean existsByUser_UserIdAndPlace_PlaceId(
            Long userId,
            Long placeId
    );

    /**
     * Find a specific bucket-list entry
     * belonging to a specific user.
     */
    Optional<BucketList> findByBucketIdAndUser_UserId(
            Long bucketId,
            Long userId
    );

    /**
     * Find a bucket-list entry using user + place.
     */
    Optional<BucketList> findByUser_UserIdAndPlace_PlaceId(
            Long userId,
            Long placeId
    );

    /**
     * Delete a saved place from a user's bucket list.
     */
    void deleteByBucketIdAndUser_UserId(
            Long bucketId,
            Long userId
    );
}