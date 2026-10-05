package com.Travel.Buddy.service.destination;

import com.Travel.Buddy.dto.destination.TouristPlaceResponse;
import com.Travel.Buddy.entity.TouristPlace;
import com.Travel.Buddy.repository.TouristPlaceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DestinationService {

    private final TouristPlaceRepository touristPlaceRepository;

    public DestinationService(
            TouristPlaceRepository touristPlaceRepository
    ) {
        this.touristPlaceRepository = touristPlaceRepository;
    }

    /**
     * Get tourist destinations for the supplied state IDs,
     * or all active destinations if no state filter is passed.
     *
     * Example:
     *
     * GET /api/v1/destinations
     *
     * GET /api/v1/destinations?stateIds=1
     *
     * GET /api/v1/destinations?stateIds=1,2,3
     */
    public List<TouristPlaceResponse> getDestinations(
            List<Long> stateIds
    ) {
        List<TouristPlace> places;

        if (stateIds == null || stateIds.isEmpty()) {
            places = touristPlaceRepository.findByActiveTrue();
        } else {
            places = touristPlaceRepository.findByState_StateIdIn(stateIds);
        }

        return places.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get a single tourist destination by ID.
     *
     * Example:
     *
     * GET /api/v1/destinations/1
     */
    public TouristPlaceResponse getDestinationById(
            Long placeId
    ) {

        TouristPlace place =
                touristPlaceRepository.findById(placeId)
                        .orElseThrow(() ->
                                new DestinationNotFoundException(
                                        "Destination not found with id: "
                                                + placeId
                                )
                        );

        return toResponse(place);
    }

    /**
     * Convert TouristPlace entity to API response.
     */
    private TouristPlaceResponse toResponse(
            TouristPlace place
    ) {

        Long stateId = null;

        if (place.getState() != null
                && place.getState().getStateId() != null) {

            /*
             * Your State entity currently uses Integer
             * for stateId.
             *
             * TouristPlaceResponse uses Long.
             *
             * Convert Integer -> Long here.
             */
            stateId =
                    place.getState()
                            .getStateId()
                            .longValue();
        }

        return new TouristPlaceResponse(
                place.getPlaceId(),
                stateId,
                place.getName(),
                place.getDescription(),
                place.getEntryFee(),
                place.getCurrency(),
                place.getImageUrl(),
                place.getLatitude(),
                place.getLongitude(),
                place.getActive()
        );
    }
}
