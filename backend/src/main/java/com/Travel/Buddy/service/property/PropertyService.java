package com.Travel.Buddy.service.property;

import com.Travel.Buddy.dto.property.PropertyDetailResponse;
import com.Travel.Buddy.dto.property.PropertyResponse;
import com.Travel.Buddy.dto.property.RoomTypeResponse;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.PropertyType;
import com.Travel.Buddy.entity.RoomType;
import com.Travel.Buddy.repository.PropertyRepository;
import com.Travel.Buddy.repository.RoomTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;

    private final PropertyDetailService propertyDetailService;

    public PropertyService(
            PropertyRepository propertyRepository,
            RoomTypeRepository roomTypeRepository,
            PropertyDetailService propertyDetailService
    ) {
        this.propertyRepository = propertyRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.propertyDetailService = propertyDetailService;
    }

    /**
     * ============================================================
     * GET PROPERTIES
     * ============================================================
     *
     * Returns only:
     *
     * - Active properties
     * - Verified properties
     *
     * Optional filters:
     *
     * - stateId
     * - propertyType
     */
    @Transactional(readOnly = true)
    public List<PropertyResponse> getProperties(
            Integer stateId,
            PropertyType propertyType
    ) {

        List<Property> properties;

        /*
         * --------------------------------------------------------
         * State + Property Type filter
         * --------------------------------------------------------
         */
        if (stateId != null && propertyType != null) {

            properties =
                    propertyRepository
                            .findByState_StateIdAndPropertyTypeAndActiveTrueAndVerifiedTrueOrderByNameAsc(
                                    stateId,
                                    propertyType
                            );

        }

        /*
         * --------------------------------------------------------
         * State filter only
         * --------------------------------------------------------
         */
        else if (stateId != null) {

            properties =
                    propertyRepository
                            .findByState_StateIdAndActiveTrueAndVerifiedTrueOrderByNameAsc(
                                    stateId
                            );

        }

        /*
         * --------------------------------------------------------
         * No filters
         * --------------------------------------------------------
         */
        else {

            properties =
                    propertyRepository
                            .findByActiveTrueAndVerifiedTrueOrderByNameAsc();
        }

        /*
         * Convert entities to DTOs while the transaction is still
         * active.
         *
         * This is important because Property.state is LAZY.
         */
        return properties.stream()
                .map(this::toPropertyResponse)
                .toList();
    }


    /**
     * ============================================================
     * GET PROPERTY DETAILS
     * ============================================================
     *
     * Returns:
     *
     * - Property information
     * - State information
     * - Room types
     */
    @Transactional(readOnly = true)
    public PropertyDetailResponse getPropertyById(
            Long propertyId
    ) {

        Property property =
                propertyRepository.findById(propertyId)
                        .orElseThrow(() ->
                                new PropertyNotFoundException(
                                        "Property not found with id: "
                                                + propertyId
                                )
                        );

        /*
         * Only verified + active properties should be publicly
         * visible, exactly as before.
         */
        if (!Boolean.TRUE.equals(property.getActive())
                || !Boolean.TRUE.equals(property.getVerified())) {

            throw new PropertyNotFoundException(
                    "Property not found with id: "
                            + propertyId
            );
        }

        /*
         * Delegated so there is exactly one place that assembles
         * a property's detail. This method previously built a
         * thin response here while a second controller built a
         * fuller one at a second URL; two detail endpoints drift
         * apart, and the one nobody remembers to call is the one
         * that rots.
         *
         * The result is a superset of what this method returned
         * before, so existing clients are unaffected.
         */
        return propertyDetailService.detail(
                propertyId, null, null
        );
    }


    /**
     * ============================================================
     * PROPERTY ENTITY → PROPERTY RESPONSE
     * ============================================================
     */
    private PropertyResponse toPropertyResponse(
            Property property
    ) {

        Integer stateId = null;
        String stateName = null;

        /*
         * State is LAZY, but this method runs inside the
         * @Transactional method above.
         */
        if (property.getState() != null) {

            stateId =
                    property.getState().getStateId();

            stateName =
                    property.getState().getName();
        }

        return new PropertyResponse(

                property.getPropertyId(),

                stateId,

                stateName,

                property.getName(),

                property.getPropertyType(),

                property.getAddress(),

                property.getDescription(),

                property.getLatitude(),

                property.getLongitude(),

                property.getVerified()
        );
    }


    /**
     * ============================================================
     * ROOM ENTITY → ROOM RESPONSE
     * ============================================================
     */
    private RoomTypeResponse toRoomTypeResponse(
            RoomType room
    ) {

        Long propertyId = null;

        if (room.getProperty() != null) {

            propertyId =
                    room.getProperty().getPropertyId();
        }

        return new RoomTypeResponse(

                room.getRoomTypeId(),

                propertyId,

                room.getCategoryName(),

                room.getMaxOccupancy(),

                room.getBasePrice(),

                room.getCurrency(),

                room.getTotalInventory(),

                room.getActive()
        );
    }
}