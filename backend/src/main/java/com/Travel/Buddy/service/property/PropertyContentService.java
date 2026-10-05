package com.Travel.Buddy.service.property;

import com.Travel.Buddy.dto.property.AmenitySummary;
import com.Travel.Buddy.dto.property.PropertyAmenityUpsertRequest;
import com.Travel.Buddy.dto.property.PropertyCancellationTierRequest;
import com.Travel.Buddy.dto.property.PropertyContentResponse;
import com.Travel.Buddy.dto.property.PropertyDetailResponse;
import com.Travel.Buddy.dto.property.PropertyImageUpsertRequest;
import com.Travel.Buddy.dto.property.PropertyPolicyUpsertRequest;
import com.Travel.Buddy.entity.Amenity;
import com.Travel.Buddy.entity.PolicyType;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.PropertyAmenity;
import com.Travel.Buddy.entity.PropertyCancellationTerm;
import com.Travel.Buddy.entity.PropertyImage;
import com.Travel.Buddy.entity.PropertyPolicy;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.AmenityRepository;
import com.Travel.Buddy.repository.PropertyAmenityRepository;
import com.Travel.Buddy.repository.PropertyCancellationTermRepository;
import com.Travel.Buddy.repository.PropertyImageRepository;
import com.Travel.Buddy.repository.PropertyPolicyRepository;
import com.Travel.Buddy.repository.PropertyRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Partner management of the detail page content. (SRS 2.3
 * section 6.2)
 *
 * <p>Every mutation returns the public detail view rather than the
 * edited row. A partner editing a cancellation tier is really
 * editing what a traveller will read, and returning the public
 * rendering makes it impossible for the two views to drift apart
 * without someone noticing during the edit that caused it.
 *
 * <p>Ownership is proved on every call, never taken from the
 * request, so guessing another partner's property id grants
 * nothing.
 */
@Service
@Transactional
public class PropertyContentService {

    private final PropertyRepository propertyRepository;
    private final AmenityRepository amenityRepository;
    private final PropertyAmenityRepository propertyAmenityRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final PropertyPolicyRepository propertyPolicyRepository;
    private final PropertyCancellationTermRepository cancellationRepository;
    private final PropertyDetailService detailService;

    public PropertyContentService(
            PropertyRepository propertyRepository,
            AmenityRepository amenityRepository,
            PropertyAmenityRepository propertyAmenityRepository,
            PropertyImageRepository propertyImageRepository,
            PropertyPolicyRepository propertyPolicyRepository,
            PropertyCancellationTermRepository cancellationRepository,
            PropertyDetailService detailService
    ) {
        this.propertyRepository = propertyRepository;
        this.amenityRepository = amenityRepository;
        this.propertyAmenityRepository = propertyAmenityRepository;
        this.propertyImageRepository = propertyImageRepository;
        this.propertyPolicyRepository = propertyPolicyRepository;
        this.cancellationRepository = cancellationRepository;
        this.detailService = detailService;
    }

    /* ============================================================
     * AMENITY CATALOGUE
     * ============================================================ */

    @Transactional(readOnly = true)
    public List<AmenitySummary> listCatalogue() {
        return amenityRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(Amenity::getCategory)
                        .thenComparing(Amenity::getLabel))
                .map(amenity -> new AmenitySummary(
                        amenity.getAmenityId(),
                        amenity.getCode(),
                        amenity.getLabel(),
                        amenity.getCategory(),
                        amenity.getIconName()
                ))
                .toList();
    }

    /* ============================================================
     * AMENITIES
     * ============================================================ */

    public PropertyDetailResponse addAmenity(
            Long partnerId,
            Long propertyId,
            PropertyAmenityUpsertRequest request
    ) {
        Property property = requireOwnedProperty(partnerId, propertyId);

        Amenity amenity = amenityRepository
                .findByCode(request.amenityCode())
                .orElseThrow(() -> PartnerApplicationException.badRequest(
                        "Unknown amenity code: "
                                + request.amenityCode()
                ));

        boolean already = propertyAmenityRepository
                .findByIdPropertyIdOrderByAmenityCategoryAscAmenityLabelAsc(
                        propertyId)
                .stream()
                .anyMatch(linked ->
                        linked.getAmenity().getAmenityId()
                                .equals(amenity.getAmenityId()));

        if (already) {
            throw PartnerApplicationException.conflict(
                    "This property already lists that amenity"
            );
        }

        propertyAmenityRepository.save(
                new PropertyAmenity(
                        property,
                        amenity,
                        isTrue(request.free()),
                        isTrue(request.requiresBooking()),
                        request.note()
                )
        );

        return published(property);
    }

    /**
     * Unlinking is a delete rather than a hide: an amenity a
     * property does not have has no history to preserve, unlike a
     * room type that bookings reference.
     */
    public PropertyDetailResponse removeAmenity(
            Long partnerId,
            Long propertyId,
            Integer amenityId
    ) {
        requireOwnedProperty(partnerId, propertyId);

        PropertyAmenity.Id id =
                new PropertyAmenity.Id(propertyId, amenityId);

        if (!propertyAmenityRepository.existsById(id)) {
            throw PartnerApplicationException.notFound(
                    "Amenity not linked to this property"
            );
        }

        propertyAmenityRepository.deleteById(id);

        return published(propertyId);
    }

    /* ============================================================
     * IMAGES
     * ============================================================ */

    public PropertyDetailResponse addImage(
            Long partnerId,
            Long propertyId,
            PropertyImageUpsertRequest request
    ) {
        Property property = requireOwnedProperty(partnerId, propertyId);

        boolean cover = isTrue(request.cover());

        if (cover) {
            clearCover(propertyId);
        }

        propertyImageRepository.save(
                new PropertyImage(
                        property,
                        request.imageUrl(),
                        request.altText(),
                        orZero(request.sortOrder()),
                        cover
                )
        );

        return published(property);
    }

    /**
     * Replace rather than mutate. The entity exposes no setters, so
     * recreating the row is also the honest expression of "this is
     * a different image now". The id changes, which nothing
     * references.
     */
    public PropertyDetailResponse updateImage(
            Long partnerId,
            Long propertyId,
            Long imageId,
            PropertyImageUpsertRequest request
    ) {
        requireOwnedProperty(partnerId, propertyId);

        propertyImageRepository
                .findByImageIdAndProperty_PropertyId(imageId, propertyId)
                .orElseThrow(() -> PartnerApplicationException.notFound(
                        "Image not found for this property"
                ));

        boolean cover = isTrue(request.cover());

        if (cover) {
            clearCover(propertyId);
        }

        propertyImageRepository.deleteById(imageId);
        propertyImageRepository.save(
                new PropertyImage(
                        requireOwnedProperty(partnerId, propertyId),
                        request.imageUrl(),
                        request.altText(),
                        orZero(request.sortOrder()),
                        cover
                )
        );

        return published(propertyId);
    }

    public PropertyDetailResponse deleteImage(
            Long partnerId,
            Long propertyId,
            Long imageId
    ) {
        requireOwnedProperty(partnerId, propertyId);

        if (!propertyImageRepository
                .existsByImageIdAndProperty_PropertyId(imageId, propertyId)) {
            throw PartnerApplicationException.notFound(
                    "Image not found for this property"
            );
        }

        propertyImageRepository.deleteById(imageId);

        return published(propertyId);
    }

    /**
     * At most one cover may exist, because the page can only lead
     * with one image and two covers would mean the first row
     * silently wins.
     */
    private void clearCover(Long propertyId) {
        propertyImageRepository
                .findByProperty_PropertyIdAndCoverTrue(propertyId)
                .ifPresent(existing -> {
                    existing.clearCoverFlag();
                    propertyImageRepository.save(existing);
                });
    }

    /* ============================================================
     * POLICIES
     * ============================================================ */

    public PropertyDetailResponse addPolicy(
            Long partnerId,
            Long propertyId,
            PropertyPolicyUpsertRequest request
    ) {
        Property property = requireOwnedProperty(partnerId, propertyId);

        propertyPolicyRepository.save(
                new PropertyPolicy(
                        property,
                        request.policyType(),
                        request.title(),
                        request.description(),
                        orZero(request.sortOrder())
                )
        );

        return published(property);
    }

    public PropertyDetailResponse updatePolicy(
            Long partnerId,
            Long propertyId,
            Long policyId,
            PropertyPolicyUpsertRequest request
    ) {
        requireOwnedProperty(partnerId, propertyId);

        PropertyPolicy policy = propertyPolicyRepository
                .findByPolicyIdAndProperty_PropertyId(policyId, propertyId)
                .orElseThrow(() -> PartnerApplicationException.notFound(
                        "Policy not found for this property"
                ));

        policy.replace(
                request.policyType(),
                request.title(),
                request.description(),
                orZero(request.sortOrder())
        );
        propertyPolicyRepository.save(policy);

        return published(propertyId);
    }

    public PropertyDetailResponse deletePolicy(
            Long partnerId,
            Long propertyId,
            Long policyId
    ) {
        requireOwnedProperty(partnerId, propertyId);

        if (!propertyPolicyRepository
                .existsByPolicyIdAndProperty_PropertyId(policyId, propertyId)) {
            throw PartnerApplicationException.notFound(
                    "Policy not found for this property"
            );
        }

        propertyPolicyRepository.deleteById(policyId);

        return published(propertyId);
    }

    /* ============================================================
     * CANCELLATION TIERS
     * ============================================================ */

    public PropertyDetailResponse addCancellationTerm(
            Long partnerId,
            Long propertyId,
            PropertyCancellationTierRequest request
    ) {
        Property property = requireOwnedProperty(partnerId, propertyId);

        List<PropertyCancellationTerm> existing =
                cancellationRepository
                        .findByProperty_PropertyIdOrderByDaysBeforeCheckInDesc(
                                propertyId);

        if (existing.stream().anyMatch(term ->
                term.getDaysBeforeCheckIn()
                        == orZero(request.daysBeforeCheckIn()))) {
            throw PartnerApplicationException.conflict(
                    "A tier for that window already exists"
            );
        }

        requireCoherent(existing, request);

        cancellationRepository.save(
                new PropertyCancellationTerm(
                        property,
                        orZero(request.daysBeforeCheckIn()),
                        request.refundPercent(),
                        orZero(request.minNightsCharge()),
                        request.description()
                )
        );

        return published(property);
    }

    public PropertyDetailResponse deleteCancellationTerm(
            Long partnerId,
            Long propertyId,
            Long termId
    ) {
        requireOwnedProperty(partnerId, propertyId);

        PropertyCancellationTerm term = cancellationRepository
                .findByTermIdAndProperty_PropertyId(termId, propertyId)
                .orElseThrow(() -> PartnerApplicationException.notFound(
                        "Cancellation tier not found for this property"
                ));

        cancellationRepository.delete(term);

        return published(propertyId);
    }

    /**
     * A refund cannot improve as the window narrows. Allowing
     * "nothing back if you cancel a week ahead, everything back if
     * you cancel tomorrow" would let a property publish a policy
     * that reads as generous while the tier a traveller actually
     * qualifies for pays nothing.
     */
    private void requireCoherent(
            List<PropertyCancellationTerm> existing,
            PropertyCancellationTierRequest candidate
    ) {
        for (PropertyCancellationTerm term : existing) {
            int candidateDays =
                    orZero(candidate.daysBeforeCheckIn());

            boolean candidateNearer =
                    candidateDays < term.getDaysBeforeCheckIn();

            boolean paysMore =
                    candidate.refundPercent()
                            .compareTo(term.getRefundPercent()) > 0;

            if (candidateNearer && paysMore) {
                throw PartnerApplicationException.badRequest(
                        "Cancelling " + candidateDays
                                + " days ahead already refunds "
                                + "less than " + term.getDaysBeforeCheckIn()
                                + " days ahead, so a nearer window "
                                + "cannot refund more"
                );
            }
        }
    }

    /* ============================================================
     * EDITABLE VIEW
     * ============================================================ */

    /**
     * The partner's own read of the content, carrying row ids so an
     * edit form can bind to them.
     *
     * <p>Kept apart from {@link PropertyDetailResponse} on purpose:
     * the public page hides row ids because a traveller has no use
     * for them, and leaking them would tie the public contract to
     * the persistence model.
     */
    @Transactional(readOnly = true)
    public PropertyContentResponse editableContent(
            Long partnerId,
            Long propertyId
    ) {
        requireOwnedProperty(partnerId, propertyId);

        return new PropertyContentResponse(
                propertyId,
                propertyAmenityRepository
                        .findByIdPropertyIdOrderByAmenityCategoryAscAmenityLabelAsc(
                                propertyId)
                        .stream()
                        .map(linked -> new PropertyContentResponse
                                .EditableAmenity(
                                linked.getAmenity().getAmenityId(),
                                linked.getAmenity().getCode(),
                                linked.getAmenity().getLabel(),
                                linked.getAmenity().getCategory(),
                                linked.getAmenity().getIconName(),
                                linked.isFree(),
                                linked.isRequiresBooking(),
                                linked.getNote()
                        ))
                        .toList(),
                propertyImageRepository
                        .findByProperty_PropertyIdOrderBySortOrderAsc(
                                propertyId)
                        .stream()
                        .map(image -> new PropertyContentResponse
                                .EditableImage(
                                image.getImageId(),
                                image.getImageUrl(),
                                image.getAltText(),
                                image.getSortOrder(),
                                image.isCover()
                        ))
                        .toList(),
                propertyPolicyRepository
                        .findByProperty_PropertyIdOrderBySortOrderAsc(
                                propertyId)
                        .stream()
                        .map(policy -> new PropertyContentResponse
                                .EditablePolicy(
                                policy.getPolicyId(),
                                policy.getPolicyType(),
                                policy.getTitle(),
                                policy.getDescription(),
                                policy.getSortOrder()
                        ))
                        .toList(),
                cancellationRepository
                        .findByProperty_PropertyIdOrderByDaysBeforeCheckInDesc(
                                propertyId)
                        .stream()
                        .map(term -> new PropertyContentResponse
                                .EditableCancellationTerm(
                                term.getTermId(),
                                term.getDaysBeforeCheckIn(),
                                term.getRefundPercent(),
                                term.penaltyPercent(),
                                term.getMinNightsCharge(),
                                term.getDescription()
                        ))
                        .toList()
        );
    }

    /* ============================================================
     * HELPERS
     * ============================================================ */

    /**
     * An absent optional flag means "not set", not "null".
     *
     * <p>Boxed on the request records on purpose. A primitive
     * boolean makes Jackson reject an omitted field outright, so a
     * partner posting the obvious partial body gets a parse error
     * instead of a request that works.
     */
    private static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * Loads a property and proves the caller owns it.
     */
    private Property requireOwnedProperty(
            Long partnerId,
            Long propertyId
    ) {
        Property property = propertyRepository
                .findById(propertyId)
                .orElseThrow(() -> PartnerApplicationException.notFound(
                        "Property not found"
                ));

        if (!property.getPartner().getUserId().equals(partnerId)) {
            throw PartnerApplicationException.forbidden(
                    "You do not own this property"
            );
        }

        return property;
    }

    private PropertyDetailResponse published(Property property) {
        return published(property.getPropertyId());
    }

    private PropertyDetailResponse published(Long propertyId) {
        return detailService.detail(propertyId, null, null);
    }
}
