package com.Travel.Buddy.service.bucketlist;

import com.Travel.Buddy.dto.bucketlist.AddBucketListRequest;
import com.Travel.Buddy.dto.bucketlist.BucketListCheckResponse;
import com.Travel.Buddy.dto.bucketlist.BucketListResponse;
import com.Travel.Buddy.entity.BucketList;
import com.Travel.Buddy.entity.TouristPlace;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.BucketListRepository;
import com.Travel.Buddy.repository.TouristPlaceRepository;
import com.Travel.Buddy.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class BucketListService {

    private final BucketListRepository bucketListRepository;
    private final TouristPlaceRepository touristPlaceRepository;
    private final UserRepository userRepository;

    public BucketListService(
            BucketListRepository bucketListRepository,
            TouristPlaceRepository touristPlaceRepository,
            UserRepository userRepository
    ) {
        this.bucketListRepository = bucketListRepository;
        this.touristPlaceRepository = touristPlaceRepository;
        this.userRepository = userRepository;
    }

    /**
     * Add a tourist place to the authenticated user's bucket list.
     */
    public BucketListResponse addToBucketList(
            String userEmail,
            AddBucketListRequest request
    ) {

        User user = getUserByEmail(userEmail);

        TouristPlace place = touristPlaceRepository
                .findById(request.placeId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Tourist place not found with id: "
                                        + request.placeId()
                        )
                );

        if (Boolean.FALSE.equals(place.getActive())) {
            throw new IllegalArgumentException(
                    "This tourist place is currently unavailable."
            );
        }

        /*
         * Prevent duplicate bucket-list entries.
         */
        if (bucketListRepository
                .existsByUser_UserIdAndPlace_PlaceId(
                        user.getUserId(),
                        place.getPlaceId()
                )) {

            throw new IllegalStateException(
                    "This tourist place is already in your bucket list."
            );
        }

        BucketList bucketList = new BucketList();

        bucketList.setUser(user);
        bucketList.setPlace(place);

        BucketList saved =
                bucketListRepository.save(bucketList);

        return toResponse(saved);
    }

    /**
     * Get all bucket-list items belonging to the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<BucketListResponse> getMyBucketList(
            String userEmail
    ) {

        User user = getUserByEmail(userEmail);

        return bucketListRepository
                .findByUser_UserIdOrderByAddedAtDesc(
                        user.getUserId()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Check whether a tourist place is saved
     * by the authenticated user.
     */
    @Transactional(readOnly = true)
    public BucketListCheckResponse checkBucketList(
            String userEmail,
            Long placeId
    ) {

        User user = getUserByEmail(userEmail);

        BucketList bucketList =
                bucketListRepository
                        .findByUser_UserIdAndPlace_PlaceId(
                                user.getUserId(),
                                placeId
                        )
                        .orElse(null);

        if (bucketList == null) {

            return new BucketListCheckResponse(
                    placeId,
                    false,
                    null
            );
        }

        return new BucketListCheckResponse(
                placeId,
                true,
                bucketList.getBucketId()
        );
    }

    /**
     * Remove a bucket-list item belonging to
     * the authenticated user.
     */
    public void removeFromBucketList(
            String userEmail,
            Long bucketId
    ) {

        User user = getUserByEmail(userEmail);

        BucketList bucketList =
                bucketListRepository
                        .findByBucketIdAndUser_UserId(
                                bucketId,
                                user.getUserId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Bucket list item not found."
                                )
                        );

        bucketListRepository.delete(bucketList);
    }

    /**
     * Load authenticated user from email.
     */
    private User getUserByEmail(
            String email
    ) {

        return userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Authenticated user not found."
                        )
                );
    }

    /**
     * Convert entity into API response.
     */
    private BucketListResponse toResponse(
            BucketList bucketList
    ) {

        TouristPlace place =
                bucketList.getPlace();

        Long stateId = null;

        if (place.getState() != null
                && place.getState().getStateId() != null) {

            stateId =
                    place.getState()
                            .getStateId()
                            .longValue();
        }

        return new BucketListResponse(
                bucketList.getBucketId(),
                place.getPlaceId(),
                place.getName(),
                place.getDescription(),
                place.getImageUrl(),
                stateId,
                bucketList.getAddedAt()
        );
    }
}