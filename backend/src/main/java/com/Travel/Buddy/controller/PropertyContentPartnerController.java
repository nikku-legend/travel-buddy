package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.property.AmenitySummary;
import com.Travel.Buddy.dto.property.PropertyAmenityUpsertRequest;
import com.Travel.Buddy.dto.property.PropertyCancellationTierRequest;
import com.Travel.Buddy.dto.property.PropertyContentResponse;
import com.Travel.Buddy.dto.property.PropertyDetailResponse;
import com.Travel.Buddy.dto.property.PropertyImageUpsertRequest;
import com.Travel.Buddy.dto.property.PropertyPolicyUpsertRequest;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.property.PropertyContentService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Hotel partner management of the detail page content. (FR-05,
 * SRS 2.3 section 6.2)
 *
 * <p>Mutations answer with the public detail view rather than the
 * edited row, so a partner always sees the page a traveller will
 * see after their change lands.
 *
 * <p>Mapped at {@code /api/v1/partner/hotel} rather than under
 * {@code /properties} because the amenity catalogue is global and
 * has no property to nest beneath.
 */
@RestController
@RequestMapping("/api/v1/partner/hotel")
public class PropertyContentPartnerController {

    private final PropertyContentService contentService;
    private final UserRepository userRepository;

    public PropertyContentPartnerController(
            PropertyContentService contentService,
            UserRepository userRepository
    ) {
        this.contentService = contentService;
        this.userRepository = userRepository;
    }

    /**
     * The shared amenity catalogue. Global, so not property scoped.
     *
     * <p>GET /api/v1/partner/hotel/amenity-codes
     */
    @GetMapping("/amenity-codes")
    public ResponseEntity<List<AmenitySummary>> amenityCatalogue(
            Authentication authentication
    ) {
        currentUserId(authentication);
        return ResponseEntity.ok(contentService.listCatalogue());
    }

    /**
     * The property's editable content, with row ids.
     *
     * <p>Distinct from the public detail payload on purpose: a
     * partner's edit form needs ids, and the traveller's page has
     * no use for them.
     *
     * <p>GET /api/v1/partner/hotel/properties/{id}/content
     */
    @GetMapping("/properties/{propertyId}/content")
    public ResponseEntity<PropertyContentResponse> content(
            Authentication authentication,
            @PathVariable Long propertyId
    ) {
        return ResponseEntity.ok(
                contentService.editableContent(
                        currentUserId(authentication),
                        propertyId
                )
        );
    }

    /* ============================================================
     * AMENITIES
     *
     * POST   /api/v1/partner/hotel/properties/{id}/amenities
     * DELETE /api/v1/partner/hotel/properties/{id}/amenities/{amenityId}
     * ============================================================ */

    @PostMapping("/properties/{propertyId}/amenities")
    public ResponseEntity<PropertyDetailResponse> addAmenity(
            Authentication authentication,
            @PathVariable Long propertyId,
            @Valid @RequestBody PropertyAmenityUpsertRequest request
    ) {
        return ResponseEntity.ok(
                contentService.addAmenity(
                        currentUserId(authentication),
                        propertyId,
                        request
                )
        );
    }

    @DeleteMapping(
            "/properties/{propertyId}/amenities/{amenityId}"
    )
    public ResponseEntity<PropertyDetailResponse> removeAmenity(
            Authentication authentication,
            @PathVariable Long propertyId,
            @PathVariable Integer amenityId
    ) {
        return ResponseEntity.ok(
                contentService.removeAmenity(
                        currentUserId(authentication),
                        propertyId,
                        amenityId
                )
        );
    }

    /* ============================================================
     * IMAGES
     *
     * POST   /api/v1/partner/hotel/properties/{id}/images
     * PUT    /api/v1/partner/hotel/properties/{id}/images/{imageId}
     * DELETE /api/v1/partner/hotel/properties/{id}/images/{imageId}
     * ============================================================ */

    @PostMapping("/properties/{propertyId}/images")
    public ResponseEntity<PropertyDetailResponse> addImage(
            Authentication authentication,
            @PathVariable Long propertyId,
            @Valid @RequestBody PropertyImageUpsertRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        contentService.addImage(
                                currentUserId(authentication),
                                propertyId,
                                request
                        )
                );
    }

    @PutMapping("/properties/{propertyId}/images/{imageId}")
    public ResponseEntity<PropertyDetailResponse> updateImage(
            Authentication authentication,
            @PathVariable Long propertyId,
            @PathVariable Long imageId,
            @Valid @RequestBody PropertyImageUpsertRequest request
    ) {
        return ResponseEntity.ok(
                contentService.updateImage(
                        currentUserId(authentication),
                        propertyId,
                        imageId,
                        request
                )
        );
    }

    @DeleteMapping("/properties/{propertyId}/images/{imageId}")
    public ResponseEntity<PropertyDetailResponse> deleteImage(
            Authentication authentication,
            @PathVariable Long propertyId,
            @PathVariable Long imageId
    ) {
        return ResponseEntity.ok(
                contentService.deleteImage(
                        currentUserId(authentication),
                        propertyId,
                        imageId
                )
        );
    }

    /* ============================================================
     * POLICIES
     *
     * POST   /api/v1/partner/hotel/properties/{id}/policies
     * PUT    /api/v1/partner/hotel/properties/{id}/policies/{policyId}
     * DELETE /api/v1/partner/hotel/properties/{id}/policies/{policyId}
     * ============================================================ */

    @PostMapping("/properties/{propertyId}/policies")
    public ResponseEntity<PropertyDetailResponse> addPolicy(
            Authentication authentication,
            @PathVariable Long propertyId,
            @Valid @RequestBody PropertyPolicyUpsertRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        contentService.addPolicy(
                                currentUserId(authentication),
                                propertyId,
                                request
                        )
                );
    }

    @PutMapping("/properties/{propertyId}/policies/{policyId}")
    public ResponseEntity<PropertyDetailResponse> updatePolicy(
            Authentication authentication,
            @PathVariable Long propertyId,
            @PathVariable Long policyId,
            @Valid @RequestBody PropertyPolicyUpsertRequest request
    ) {
        return ResponseEntity.ok(
                contentService.updatePolicy(
                        currentUserId(authentication),
                        propertyId,
                        policyId,
                        request
                )
        );
    }

    @DeleteMapping("/properties/{propertyId}/policies/{policyId}")
    public ResponseEntity<PropertyDetailResponse> deletePolicy(
            Authentication authentication,
            @PathVariable Long propertyId,
            @PathVariable Long policyId
    ) {
        return ResponseEntity.ok(
                contentService.deletePolicy(
                        currentUserId(authentication),
                        propertyId,
                        policyId
                )
        );
    }

    /* ============================================================
     * CANCELLATION TIERS
     *
     * POST   /api/v1/partner/hotel/properties/{id}/cancellation-terms
     * DELETE /api/v1/partner/hotel/properties/{id}/cancellation-terms/{termId}
     *
     * No PUT: a tier is defined by its window, and moving that
     * window is a different policy, so it is removed and re-added
     * rather than silently rewriting a term a traveller may have
     * already read.
     * ============================================================ */

    @PostMapping(
            "/properties/{propertyId}/cancellation-terms"
    )
    public ResponseEntity<PropertyDetailResponse> addCancellationTerm(
            Authentication authentication,
            @PathVariable Long propertyId,
            @Valid @RequestBody PropertyCancellationTierRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        contentService.addCancellationTerm(
                                currentUserId(authentication),
                                propertyId,
                                request
                        )
                );
    }

    @DeleteMapping(
            "/properties/{propertyId}/cancellation-terms/{termId}"
    )
    public ResponseEntity<PropertyDetailResponse> deleteCancellationTerm(
            Authentication authentication,
            @PathVariable Long propertyId,
            @PathVariable Long termId
    ) {
        return ResponseEntity.ok(
                contentService.deleteCancellationTerm(
                        currentUserId(authentication),
                        propertyId,
                        termId
                )
        );
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "Authentication is required"
            );
        }

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found"
                ));

        return user.getUserId();
    }
}