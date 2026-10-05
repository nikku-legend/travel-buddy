package com.Travel.Buddy.service.property;

import com.Travel.Buddy.dto.availability.AvailabilityResponse;
import com.Travel.Buddy.dto.availability.DailyAvailabilityResponse;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.RoomInventoryDaily;
import com.Travel.Buddy.entity.RoomType;
import com.Travel.Buddy.repository.PropertyRepository;
import com.Travel.Buddy.repository.RoomInventoryRepository;
import com.Travel.Buddy.repository.RoomTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class AvailabilityService {

    private final PropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomInventoryRepository roomInventoryRepository;

    public AvailabilityService(
            PropertyRepository propertyRepository,
            RoomTypeRepository roomTypeRepository,
            RoomInventoryRepository roomInventoryRepository
    ) {
        this.propertyRepository = propertyRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.roomInventoryRepository = roomInventoryRepository;
    }

    /**
     * ============================================================
     * CHECK ROOM AVAILABILITY
     * ============================================================
     */
    @Transactional(readOnly = true)
    public List<AvailabilityResponse> checkAvailability(
            Long propertyId,
            LocalDate checkIn,
            LocalDate checkOut,
            Integer guests,
            Integer rooms
    ) {

        validateRequest(
                checkIn,
                checkOut,
                guests,
                rooms
        );

        /*
         * --------------------------------------------------------
         * Find property
         * --------------------------------------------------------
         */
        Property property =
                propertyRepository.findById(propertyId)
                        .orElseThrow(() ->
                                new PropertyNotFoundException(
                                        "Property not found with id: "
                                                + propertyId
                                )
                        );

        /*
         * Only active + verified properties can be booked.
         */
        if (!Boolean.TRUE.equals(property.getActive())
                || !Boolean.TRUE.equals(property.getVerified())) {

            throw new PropertyNotFoundException(
                    "Property is not available"
            );
        }

        /*
         * --------------------------------------------------------
         * Find active room types
         * --------------------------------------------------------
         */
        List<RoomType> roomTypes =
                roomTypeRepository
                        .findByProperty_PropertyIdAndActiveTrueOrderByBasePriceAsc(
                                propertyId
                        );

        List<AvailabilityResponse> responses =
                new ArrayList<>();

        long requiredNights =
                ChronoUnit.DAYS.between(
                        checkIn,
                        checkOut
                );

        /*
         * --------------------------------------------------------
         * Check each room type
         * --------------------------------------------------------
         */
        for (RoomType roomType : roomTypes) {

            /*
             * One room must be capable of handling the requested
             * number of guests.
             */
            if (roomType.getMaxOccupancy() < guests) {
                continue;
            }

            /*
             * ----------------------------------------------------
             * Get daily inventory
             * ----------------------------------------------------
             */
            List<RoomInventoryDaily> inventory =
                    roomInventoryRepository.findForAvailabilityReadOnly(
                            roomType.getRoomTypeId(),
                            checkIn,
                            checkOut
                    );

            /*
             * We need one inventory record for every night.
             */
            if (inventory.size() != requiredNights) {
                continue;
            }

            /*
             * Find the smallest available inventory during the
             * entire stay.
             *
             * Example:
             *
             * Night 1 → 10 available
             * Night 2 → 8 available
             * Night 3 → 6 available
             *
             * Result = 6
             *
             * Therefore a maximum of 6 rooms can be requested.
             */
            int minimumAvailableRooms =
                    inventory.stream()
                            .mapToInt(
                                    RoomInventoryDaily::getAvailableInventory
                            )
                            .min()
                            .orElse(0);

            /*
             * Not enough rooms.
             */
            if (minimumAvailableRooms < rooms) {
                continue;
            }

            /*
             * ----------------------------------------------------
             * Daily availability response
             * ----------------------------------------------------
             */
            List<DailyAvailabilityResponse> daily =
                    inventory.stream()
                            .map(item ->
                                    new DailyAvailabilityResponse(
                                            item.getInventoryDate(),
                                            item.getTotalInventory(),
                                            item.getReservedRooms(),
                                            item.getAvailableInventory()
                                    )
                            )
                            .toList();

            /*
             * ----------------------------------------------------
             * Calculate total price
             * ----------------------------------------------------
             *
             * price per night
             * × number of nights
             * × number of rooms
             */
            BigDecimal totalPrice =
                    roomType.getBasePrice()
                            .multiply(
                                    BigDecimal.valueOf(
                                            requiredNights
                                    )
                            )
                            .multiply(
                                    BigDecimal.valueOf(
                                            rooms
                                    )
                            );

            /*
             * ----------------------------------------------------
             * Build response
             * ----------------------------------------------------
             */
            responses.add(
                    new AvailabilityResponse(

                            propertyId,

                            roomType.getRoomTypeId(),

                            roomType.getCategoryName(),

                            roomType.getMaxOccupancy(),

                            roomType.getBasePrice(),

                            roomType.getCurrency(),

                            rooms,

                            minimumAvailableRooms,

                            roomType.getTotalInventory(),

                            (int) requiredNights,

                            totalPrice,

                            checkIn,

                            checkOut,

                            guests,

                            daily
                    )
            );
        }

        return responses;
    }


    /**
     * ============================================================
     * REQUEST VALIDATION
     * ============================================================
     */
    private void validateRequest(
            LocalDate checkIn,
            LocalDate checkOut,
            Integer guests,
            Integer rooms
    ) {

        /*
         * Dates required.
         */
        if (checkIn == null
                || checkOut == null) {

            throw new AvailabilityException(
                    "Check-in and check-out dates are required"
            );
        }

        /*
         * Check-in must be before check-out.
         */
        if (!checkIn.isBefore(checkOut)) {

            throw new AvailabilityException(
                    "Check-out must be after check-in"
            );
        }

        /*
         * Cannot book dates in the past.
         */
        if (checkIn.isBefore(LocalDate.now())) {

            throw new AvailabilityException(
                    "Check-in cannot be in the past"
            );
        }

        /*
         * Guests.
         */
        if (guests == null || guests < 1) {

            throw new AvailabilityException(
                    "At least one guest is required"
            );
        }

        /*
         * Rooms.
         */
        if (rooms == null || rooms < 1) {

            throw new AvailabilityException(
                    "At least one room is required"
            );
        }

        /*
         * Maximum stay duration.
         */
        long nights =
                ChronoUnit.DAYS.between(
                        checkIn,
                        checkOut
                );

        if (nights > 30) {

            throw new AvailabilityException(
                    "Stay duration cannot exceed 30 nights"
            );
        }
    }
}
