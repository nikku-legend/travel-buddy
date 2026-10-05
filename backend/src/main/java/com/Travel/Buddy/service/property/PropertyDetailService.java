package com.Travel.Buddy.service.property;

import com.Travel.Buddy.dto.property.PropertyDetailResponse;
import com.Travel.Buddy.entity.Amenity;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.PropertyAmenity;
import com.Travel.Buddy.entity.PropertyCancellationTerm;
import com.Travel.Buddy.entity.PropertyImage;
import com.Travel.Buddy.entity.PropertyPolicy;
import com.Travel.Buddy.entity.ReviewSummary;
import com.Travel.Buddy.entity.ReviewTargetType;
import com.Travel.Buddy.entity.RoomInventoryDaily;
import com.Travel.Buddy.entity.RoomType;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.PropertyAmenityRepository;
import com.Travel.Buddy.repository.PropertyCancellationTermRepository;
import com.Travel.Buddy.repository.PropertyImageRepository;
import com.Travel.Buddy.repository.PropertyPolicyRepository;
import com.Travel.Buddy.repository.PropertyRepository;
import com.Travel.Buddy.repository.ReviewSummaryRepository;
import com.Travel.Buddy.repository.RoomInventoryRepository;
import com.Travel.Buddy.repository.RoomTypeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hotel detail page. (SRS 2.3 section 6.2)
 *
 * <p>One endpoint assembles the whole page. Section 6.2 lists
 * eleven things the page must show, and fetching them separately
 * would let a traveller see a price from one response beside an
 * availability from another taken a moment earlier.
 *
 * <p>Public and read-only. Nothing here reserves anything: a
 * detail view is a look, and must never hold inventory.
 */
@Service
public class PropertyDetailService {

    private final PropertyRepository propertyRepository;
    private final PropertyAmenityRepository amenityRepository;
    private final PropertyImageRepository imageRepository;
    private final PropertyPolicyRepository policyRepository;
    private final PropertyCancellationTermRepository cancellationRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomInventoryRepository inventoryRepository;
    private final ReviewSummaryRepository reviewSummaryRepository;

    public PropertyDetailService(
            PropertyRepository propertyRepository,
            PropertyAmenityRepository amenityRepository,
            PropertyImageRepository imageRepository,
            PropertyPolicyRepository policyRepository,
            PropertyCancellationTermRepository cancellationRepository,
            RoomTypeRepository roomTypeRepository,
            RoomInventoryRepository inventoryRepository,
            ReviewSummaryRepository reviewSummaryRepository
    ) {
        this.propertyRepository = propertyRepository;
        this.amenityRepository = amenityRepository;
        this.imageRepository = imageRepository;
        this.policyRepository = policyRepository;
        this.cancellationRepository = cancellationRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.inventoryRepository = inventoryRepository;
        this.reviewSummaryRepository = reviewSummaryRepository;
    }

    /**
     * @param checkIn optional. Availability is only meaningful
     *                against a date range, so when absent the
     *                field is null rather than a misleading
     *                "available"
     */
    @Transactional(readOnly = true)
    public PropertyDetailResponse detail(
            Long propertyId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Property not found"
                        )
                );

        if (checkIn != null && checkOut != null
                && checkOut.isBefore(checkIn)) {
            throw PartnerApplicationException.badRequest(
                    "The end date cannot be before the start date"
            );
        }

        ReviewSummary summary = reviewSummaryRepository
                .findByTargetTypeAndTargetId(
                        ReviewTargetType.HOTEL,
                        propertyId
                )
                .orElse(null);

        List<PropertyCancellationTerm> terms =
                cancellationRepository
                        .findByProperty_PropertyIdOrderByDaysBeforeCheckInDesc(
                                propertyId
                        );

        Integer mostGenerous = terms.isEmpty()
                ? null
                : terms.get(0).getDaysBeforeCheckIn();

        return new PropertyDetailResponse(
                propertyId,
                property.getState() == null
                        ? null
                        : property.getState().getStateId(),
                property.getName(),
                property.getDescription(),
                property.getAddress(),
                property.getCity() == null
                        ? null
                        : property.getCity().getName(),
                property.getState() == null
                        ? null
                        : property.getState().getName(),
                property.getPropertyType(),
                property.getLatitude(),
                property.getLongitude(),
                imagesOf(propertyId),
                amenityGroupsOf(propertyId),
                roomOptionsOf(propertyId),
                summary == null
                        ? null
                        : summary.getAverageRating(),
                summary == null
                        ? 0
                        : summary.getReviewCount(),
                Boolean.TRUE.equals(property.getVerified()),
                policiesOf(propertyId),
                tiersOf(terms),
                availabilityOf(
                        propertyId, checkIn, checkOut
                ),
                mostGenerous
        );
    }

    private List<PropertyDetailResponse.Image> imagesOf(
            Long propertyId
    ) {
        return imageRepository
                .findByProperty_PropertyIdOrderBySortOrderAsc(
                        propertyId
                )
                .stream()
                .map(image -> new PropertyDetailResponse.Image(
                        image.getImageId(),
                        image.getImageUrl(),
                        image.getAltText(),
                        image.isCover()
                ))
                .toList();
    }

    /**
     * Grouped so the page renders "Facilities", "Access" and so
     * on, rather than one undifferentiated wall of twenty items.
     */
    private List<PropertyDetailResponse.AmenityGroup> amenityGroupsOf(
            Long propertyId
    ) {
        Map<String, List<PropertyDetailResponse.Entry>> grouped =
                new LinkedHashMap<>();

        for (PropertyAmenity link : amenityRepository
                .findByIdPropertyIdOrderByAmenityCategoryAscAmenityLabelAsc(
                        propertyId
                )) {

            Amenity amenity = link.getAmenity();

            grouped.computeIfAbsent(
                    amenity.getCategory(),
                    category -> new ArrayList<>()
            ).add(new PropertyDetailResponse.Entry(
                    amenity.getCode(),
                    amenity.getLabel(),
                    amenity.getIconName(),
                    link.isFree(),
                    link.isRequiresBooking(),
                    link.getNote()
            ));
        }

        return grouped.entrySet().stream()
                .map(entry ->
                        new PropertyDetailResponse.AmenityGroup(
                                entry.getKey(),
                                entry.getValue()
                        ))
                .toList();
    }

    private List<PropertyDetailResponse.RoomOption> roomOptionsOf(
            Long propertyId
    ) {
        return roomTypeRepository
                .findByProperty_PropertyIdOrderByCategoryNameAsc(
                        propertyId
                )
                .stream()
                .map(room -> new PropertyDetailResponse.RoomOption(
                        room.getRoomTypeId(),
                        room.getCategoryName(),
                        room.getMaxOccupancy() == null
                                ? 0
                                : room.getMaxOccupancy(),
                        room.getBasePrice(),
                        room.getCurrency(),
                        room.getTotalInventory() == null
                                ? 0
                                : room.getTotalInventory(),
                        !Boolean.FALSE.equals(room.getActive())
                ))
                .toList();
    }

    private List<PropertyDetailResponse.Policy> policiesOf(
            Long propertyId
    ) {
        return policyRepository
                .findByProperty_PropertyIdOrderBySortOrderAsc(
                        propertyId
                )
                .stream()
                .map(policy -> new PropertyDetailResponse.Policy(
                        policy.getPolicyType(),
                        policy.getTitle(),
                        policy.getDescription()
                ))
                .toList();
    }

    private List<PropertyDetailResponse.CancellationTier> tiersOf(
            List<PropertyCancellationTerm> terms
    ) {
        return terms.stream()
                .map(term ->
                        new PropertyDetailResponse.CancellationTier(
                                term.getDaysBeforeCheckIn(),
                                term.getRefundPercent(),
                                term.penaltyPercent(),
                                term.getMinNightsCharge(),
                                term.getDescription()
                        ))
                .toList();
    }

    /**
     * Availability for the whole requested range, checked against
     * the best room type.
     *
     * <p>Null when no dates were asked for, which is
     * deliberately different from "available": a property with
     * no free rooms is not a property a traveller can book, and
     * saying so plainly beats a blank field.
     */
    private PropertyDetailResponse.Availability availabilityOf(
            Long propertyId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        if (checkIn == null || checkOut == null
                || !checkOut.isAfter(checkIn)) {
            return null;
        }

        int nights = (int) ChronoUnit.DAYS.between(
                checkIn, checkOut
        );

        boolean anyRoom = false;
        boolean anyFullRange = false;

        for (RoomType room : roomTypeRepository
                .findByProperty_PropertyIdOrderByCategoryNameAsc(
                        propertyId
                )) {

            if (Boolean.FALSE.equals(room.getActive())) {
                continue;
            }

            anyRoom = true;

            List<RoomInventoryDaily> days =
                    inventoryRepository.findByRoomTypeAndDateRange(
                            room.getRoomTypeId(),
                            checkIn,
                            checkOut.minusDays(1)
                    );

            if (days.size() != nights) {
                continue;
            }

            boolean allOpen = days.stream().allMatch(
                    day -> day.getAvailableInventory() != null
                            && day.getAvailableInventory() > 0
            );

            if (allOpen) {
                anyFullRange = true;
                break;
            }
        }

        if (anyFullRange) {
            return new PropertyDetailResponse.Availability(
                    checkIn, checkOut, nights, true, null
            );
        }

        return new PropertyDetailResponse.Availability(
                checkIn, checkOut, nights, false,
                anyRoom
                        ? "No rooms available for the whole stay"
                        : "This property has no rooms listed"
        );
    }
}