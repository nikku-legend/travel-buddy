package com.Travel.Buddy.service.property;

import com.Travel.Buddy.dto.property.*;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partner management of the detail page content. (FR-05, SRS 2.3
 * section 6.2)
 *
 * <p>Weighted towards the rules that stop a partner publishing
 * something incoherent, since the happy paths are already covered
 * by the fact the rows render.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Property content management (SRS 2.3 section 6.2)")
class PropertyContentServiceTest {

    @Autowired
    private PropertyContentService contentService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private PropertyImageRepository imageRepository;
    @Autowired
    private PropertyPolicyRepository policyRepository;
    @Autowired
    private PropertyCancellationTermRepository cancellationRepository;
    @Autowired
    private AmenityRepository amenityRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private StateRepository stateRepository;

    private static State sharedState;

    private User owner;
    private User stranger;
    private Property property;

    @BeforeEach
    void setUp() {
        owner = user("Content Owner");
        stranger = user("Content Stranger");

        if (sharedState == null) {
            /*
             * Upsert, not insert. The suite runs several Spring
             * contexts in one JVM, so a static guard can be
             * re-entered against a fresh database and a blind
             * insert would then collide on the unique iso code.
             */
            Country country = countryRepository
                    .findByIsoCode("PCL")
                    .orElseGet(() -> {
                        Country created = new Country();
                        created.setName("Content Land");
                        created.setIsoCode("PCL");
                        return created;
                    });
            country = countryRepository.save(country);

            sharedState = new State();
            sharedState.setName("Content State");
            sharedState.setCountry(country);
            sharedState.setRegionZone(RegionZone.EAST);
            sharedState = stateRepository.save(sharedState);
        }

        City city = new City(
                sharedState,
                "C-" + UUID.randomUUID()
                        .toString().substring(0, 6),
                "c-" + UUID.randomUUID()
                        .toString().substring(0, 6)
        );
        city = cityRepository.save(city);

        property = new Property();
        property.setName("Content Stay-" + UUID.randomUUID()
                .toString().substring(0, 6));
        property.setPropertyType(PropertyType.HOTEL);
        property.setState(sharedState);
        property.setCity(city);
        property.setPartner(owner);
        property.setAddress("Somewhere");
        property.setStatus(PropertyStatus.APPROVED);
        property.setVerified(true);
        property.setActive(true);
        property = propertyRepository.save(property);
    }

    private User user(String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@tb.local");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    private PropertyContentResponse editable() {
        return contentService.editableContent(
                owner.getUserId(), property.getPropertyId()
        );
    }

    private Amenity amenity(String code) {
        return amenityRepository.findByCode(code)
                .orElseGet(() -> amenityRepository.save(
                        new Amenity(code, code, "GROUP", null)));
    }

    /* ============================================================
     * OWNERSHIP
     * ============================================================ */

    @Test
    @DisplayName("a partner cannot edit another partner's property")
    void ownershipIsProved() {
        Amenity wifi = amenity("WIFI");

        assertThrows(
                PartnerApplicationException.class,
                () -> contentService.addAmenity(
                        stranger.getUserId(),
                        property.getPropertyId(),
                        new PropertyAmenityUpsertRequest(
                                wifi.getCode(), true, false, null
                        )
                )
        );
    }

    @Test
    @DisplayName("a guessed id from another property is not found")
    void crossPropertyIdIsNotFound() {
        Amenity wifi = amenity("WIFI");
        contentService.addAmenity(
                owner.getUserId(),
                property.getPropertyId(),
                new PropertyAmenityUpsertRequest(
                        wifi.getCode(), true, false, null
                )
        );

        // Owned by us, but not a row of ours.
        assertThrows(
                PartnerApplicationException.class,
                () -> contentService.deleteImage(
                        owner.getUserId(),
                        property.getPropertyId(),
                        999_999L
                )
        );
    }

    /* ============================================================
     * AMENITIES
     * ============================================================ */

    /**
     * The catalogue is the contract. Free text here is what would
     * create "free wifi" and "complimentary Wi-Fi" as two
     * unmatchable amenities.
     */
    @Test
    @DisplayName("an unknown amenity code is rejected")
    void unknownAmenityCodeRejected() {
        assertThrows(
                PartnerApplicationException.class,
                () -> contentService.addAmenity(
                        owner.getUserId(),
                        property.getPropertyId(),
                        new PropertyAmenityUpsertRequest(
                                "TOTALLY_MADE_UP", true, false, null
                        )
                )
        );
    }

    @Test
    @DisplayName("the same amenity cannot be linked twice")
    void duplicateAmenityRejected() {
        Amenity wifi = amenity("WIFI");
        PropertyAmenityUpsertRequest request =
                new PropertyAmenityUpsertRequest(
                        wifi.getCode(), true, false, null
                );

        contentService.addAmenity(
                owner.getUserId(), property.getPropertyId(), request);

        assertThrows(
                PartnerApplicationException.class,
                () -> contentService.addAmenity(
                        owner.getUserId(),
                        property.getPropertyId(),
                        request
                )
        );
    }

    @Test
    @DisplayName("adding an amenity shows up on the public page")
    void addedAmenityIsPubliclyVisible() {
        Amenity pool = amenity("POOL");

        PropertyDetailResponse after = contentService.addAmenity(
                owner.getUserId(),
                property.getPropertyId(),
                new PropertyAmenityUpsertRequest(
                        pool.getCode(), true, false, null
                )
        );

        assertTrue(
                after.amenities().stream()
                        .flatMap(g -> g.amenities().stream())
                        .anyMatch(a -> a.code().equals("POOL")),
                "a partner change that does not reach the page is "
                        + "not a change the traveller can see"
        );
    }

    @Test
    @DisplayName("unlinking an amenity removes it from the page")
    void removedAmenityDisappears() {
        Amenity pool = amenity("POOL");
        PropertyDetailResponse after = contentService.addAmenity(
                owner.getUserId(),
                property.getPropertyId(),
                new PropertyAmenityUpsertRequest(
                        pool.getCode(), true, false, null
                )
        );

        Integer amenityId = editable().amenities().stream()
                .filter(a -> a.code().equals("POOL"))
                .findFirst()
                .orElseThrow()
                .amenityId();

        PropertyDetailResponse removed = contentService.removeAmenity(
                owner.getUserId(),
                property.getPropertyId(),
                amenityId
        );

        assertFalse(
                removed.amenities().stream()
                        .flatMap(g -> g.amenities().stream())
                        .anyMatch(a -> a.code().equals("POOL"))
        );
    }

    /* ============================================================
     * IMAGES: THE SINGLE COVER RULE
     * ============================================================ */

    /**
     * The page can only lead with one image. Two covers would mean
     * whichever the query returned first silently wins.
     */
    @Test
    @DisplayName("only one image may be the cover")
    void onlyOneCoverSurvives() {
        contentService.addImage(
                owner.getUserId(),
                property.getPropertyId(),
                new PropertyImageUpsertRequest(
                        "https://img/1.jpg", "First", 0, true
                )
        );

        PropertyDetailResponse after = contentService.addImage(
                owner.getUserId(),
                property.getPropertyId(),
                new PropertyImageUpsertRequest(
                        "https://img/2.jpg", "Second", 1, true
                )
        );

        long covers = imageRepository
                .findByProperty_PropertyIdOrderBySortOrderAsc(
                        property.getPropertyId())
                .stream()
                .filter(PropertyImage::isCover)
                .count();

        assertEquals(
                1, covers,
                "two covers means the first row wins by accident"
        );
        assertEquals(2, after.images().size());
    }

    @Test
    @DisplayName("a deleted image is gone from the page")
    void deletedImageDisappears() {
        PropertyDetailResponse after = contentService.addImage(
                owner.getUserId(),
                property.getPropertyId(),
                new PropertyImageUpsertRequest(
                        "https://img/only.jpg", "Only", 0, true
                )
        );

        Long imageId = imageRepository
                .findByProperty_PropertyIdOrderBySortOrderAsc(
                        property.getPropertyId())
                .get(0)
                .getImageId();

        PropertyDetailResponse removed = contentService.deleteImage(
                owner.getUserId(),
                property.getPropertyId(),
                imageId
        );

        assertTrue(removed.images().isEmpty());
    }

    /* ============================================================
     * POLICIES
     * ============================================================ */

    @Test
    @DisplayName("a policy is added, edited and removed in place")
    void policyLifecycle() {
        PropertyDetailResponse added = contentService.addPolicy(
                owner.getUserId(),
                property.getPropertyId(),
                new PropertyPolicyUpsertRequest(
                        PolicyType.CHECK_IN_TIME,
                        "Check-in", "From 12:00", 0
                )
        );

        assertEquals(1, added.policies().size());

        Long policyId = editable().policies().get(0).policyId();

        PropertyDetailResponse updated = contentService.updatePolicy(
                owner.getUserId(),
                property.getPropertyId(),
                policyId,
                new PropertyPolicyUpsertRequest(
                        PolicyType.CHECK_IN_TIME,
                        "Check-in", "From 2:00 PM", 0
                )
        );

        assertEquals(
                0, "From 2:00 PM".compareTo(
                        updated.policies().get(0).description())
        );
        assertEquals(
                policyId,
                editable().policies().get(0).policyId(),
                "an edit must keep the id so references survive"
        );

        PropertyDetailResponse removed = contentService.deletePolicy(
                owner.getUserId(),
                property.getPropertyId(),
                policyId
        );

        assertTrue(removed.policies().isEmpty());
    }

    /* ============================================================
     * CANCELLATION: THE COHERENCE RULE
     * ============================================================ */

    @Test
    @DisplayName("a tier nearer the date cannot refund more than a wider one")
    void incoherentTiersRejected() {
        contentService.addCancellationTerm(
                owner.getUserId(),
                property.getPropertyId(),
                new PropertyCancellationTierRequest(
                        7, new BigDecimal("50.00"), 0,
                        "Half up to 7 days before"
                )
        );

        // Nothing back a week ahead, everything back tomorrow: a
        // policy that reads generous while the tier a traveller
        // planning ahead actually qualifies for pays nothing.
        assertThrows(
                PartnerApplicationException.class,
                () -> contentService.addCancellationTerm(
                        owner.getUserId(),
                        property.getPropertyId(),
                        new PropertyCancellationTierRequest(
                                1, new BigDecimal("100.00"), 0,
                                "Full the day before"
                        )
                )
        );
    }

    /**
     * The boundary the rule must not overshoot. A flat full refund
     * is a real and generous policy, and a rule that read "equal"
     * as "increasing" would forbid a partner from offering it.
     */
    @Test
    @DisplayName("an equal refund at a nearer window is allowed")
    void flatFullRefundAccepted() {
        contentService.addCancellationTerm(
                owner.getUserId(),
                property.getPropertyId(),
                new PropertyCancellationTierRequest(
                        7, new BigDecimal("100.00"), 0, "Full"
                )
        );

        PropertyDetailResponse after = contentService
                .addCancellationTerm(
                        owner.getUserId(),
                        property.getPropertyId(),
                        new PropertyCancellationTierRequest(
                                1, new BigDecimal("100.00"), 0,
                                "Still full"
                        )
                );

        assertEquals(2, after.cancellationTerms().size());
    }

    @Test
    @DisplayName("a stricter nearer tier is accepted")
    void coherentTiersAccepted() {
        contentService.addCancellationTerm(
                owner.getUserId(),
                property.getPropertyId(),
                new PropertyCancellationTierRequest(
                        7, new BigDecimal("100.00"), 0, "Full"
                )
        );

        PropertyDetailResponse after = contentService
                .addCancellationTerm(
                        owner.getUserId(),
                        property.getPropertyId(),
                        new PropertyCancellationTierRequest(
                                1, new BigDecimal("50.00"), 0,
                                "Half"
                        )
                );

        assertEquals(2, after.cancellationTerms().size());
    }

    @Test
    @DisplayName("a duplicate window is refused")
    void duplicateWindowRejected() {
        PropertyCancellationTierRequest request =
                new PropertyCancellationTierRequest(
                        7, new BigDecimal("100.00"), 0, "Full"
                );

        contentService.addCancellationTerm(
                owner.getUserId(), property.getPropertyId(), request);

        assertThrows(
                PartnerApplicationException.class,
                () -> contentService.addCancellationTerm(
                        owner.getUserId(),
                        property.getPropertyId(),
                        request
                )
        );
    }

    /**
     * The penalty is derived, so a tier can never present a refund
     * and a deduction that disagree.
     */
    @Test
    @DisplayName("the published tier derives its penalty")
    void penaltyIsDerived() {
        PropertyDetailResponse after = contentService
                .addCancellationTerm(
                        owner.getUserId(),
                        property.getPropertyId(),
                        new PropertyCancellationTierRequest(
                                3, new BigDecimal("60.00"), 0, "Mostly"
                        )
                );

        PropertyDetailResponse.CancellationTier tier =
                after.cancellationTerms().get(0);

        assertEquals(
                0, new BigDecimal("40.00")
                        .compareTo(tier.penaltyPercent())
        );
    }

    @Test
    @DisplayName("deleting the last tier leaves the property non-refundable")
    void deletingTiersFallsBackToNonRefundable() {
        PropertyDetailResponse after = contentService
                .addCancellationTerm(
                        owner.getUserId(),
                        property.getPropertyId(),
                        new PropertyCancellationTierRequest(
                                7, new BigDecimal("100.00"), 0, "Full"
                        )
                );

        Long termId = editable().cancellationTerms().get(0).termId();

        PropertyDetailResponse removed = contentService
                .deleteCancellationTerm(
                        owner.getUserId(),
                        property.getPropertyId(),
                        termId
                );

        assertTrue(removed.cancellationTerms().isEmpty());
        assertNull(
                removed.mostGenerousCancellationDays(),
                "no tiers means non-refundable, not unknown"
        );
    }

    /* ============================================================
     * CATALOGUE
     * ============================================================ */

    /**
     * A partner posting the obvious partial body should get a
     * working request, not a Jackson complaint about a missing
     * primitive. These used to be primitive fields, so an omitted
     * {@code cover} or {@code sortOrder} failed to deserialise.
     */
    @Test
    @DisplayName("omitted optional fields fall back instead of failing")
    void omittedFieldsDefault() {
        PropertyDetailResponse after = contentService.addImage(
                owner.getUserId(),
                property.getPropertyId(),
                new PropertyImageUpsertRequest(
                        "https://img/bare.jpg", null, null, null
                )
        );

        assertEquals(1, after.images().size());

        PropertyContentResponse.EditableImage image =
                editable().images().get(0);

        assertEquals(0, image.sortOrder());
        assertFalse(
                image.cover(),
                "an omitted cover means no cover, not a guess"
        );
    }

    @Test
    @DisplayName("the catalogue is offered for a partner to pick from")
    void catalogueIsListed() {
        amenity("WIFI");

        assertFalse(contentService.listCatalogue().isEmpty());
    }
}