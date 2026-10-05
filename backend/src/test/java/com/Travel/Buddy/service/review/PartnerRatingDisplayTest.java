package com.Travel.Buddy.service.review;

import com.Travel.Buddy.dto.cab.CabResponse;
import com.Travel.Buddy.dto.guide.GuideSummaryResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.repository.*;
import com.Travel.Buddy.service.cab.CabService;
import com.Travel.Buddy.service.guide.GuideService;

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
 * How a guide or cab's rating reaches the page. (FR-26, SRS 2.3)
 *
 * <p>These exist because both entities carry a {@code rating}
 * column that defaulted to 5.00 and that nothing in the
 * application ever wrote. Reviews for guides and cabs were
 * aggregated correctly into the review summary and then never
 * read, so every guide and cab displayed a flawless rating no
 * matter what travellers actually said.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Guide and cab rating display (FR-26)")
class PartnerRatingDisplayTest {

    @Autowired
    private GuideService guideService;
    @Autowired
    private CabService cabService;
    @Autowired
    private GuideRepository guideRepository;
    @Autowired
    private CabRepository cabRepository;
    @Autowired
    private ReviewSummaryRepository reviewSummaryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private StateRepository stateRepository;

    private static State sharedState;

    private User partner;
    private Guide guide;
    private Cab cab;

    @BeforeEach
    void setUp() {
        partner = user("Rating Partner");

        if (sharedState == null) {
            Country country = countryRepository
                    .findByIsoCode("PRT")
                    .orElseGet(() -> {
                        Country created = new Country();
                        created.setName("Rating Land");
                        created.setIsoCode("PRT");
                        return created;
                    });
            country = countryRepository.save(country);

            sharedState = new State();
            sharedState.setName("Rating State");
            sharedState.setCountry(country);
            sharedState.setRegionZone(RegionZone.EAST);
            sharedState = stateRepository.save(sharedState);
        }

        guide = new Guide();
        guide.setUser(partner);
        guide.setState(sharedState);
        guide.setDailyRate(new BigDecimal("1500.00"));
        guide.setBio("A guide");
        guide.setVerified(true);
        guide.setActive(true);
        guide = guideRepository.save(guide);

        cab = new Cab();
        cab.setPartner(partner);
        cab.setState(sharedState);
        cab.setVehicleName("Sedan");
        cab.setVehicleType(VehicleType.SEDAN);
        cab.setRegistrationNumber(
                "OD" + UUID.randomUUID()
                        .toString().substring(0, 4).toUpperCase());
        cab.setSeatingCapacity(4);
        cab.setDriverName("Driver");
        cab.setDriverPhone("9999999999");
        cab.setPricePerKm(new BigDecimal("20.00"));
        cab.setBaseFare(new BigDecimal("100.00"));
        cab.setAvailable(true);
        cab.setVerified(true);
        cab.setActive(true);
        cab = cabRepository.save(cab);
    }

    private User user(String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@tb.local");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    private void summarise(
            ReviewTargetType type,
            Long id,
            String average,
            int count
    ) {
        ReviewSummary summary = new ReviewSummary();
        summary.setTargetType(type);
        summary.setTargetId(id);
        summary.setAverageRating(new BigDecimal(average));
        summary.setReviewCount(count);
        summary.setOneStarCount(0);
        summary.setTwoStarCount(0);
        summary.setThreeStarCount(0);
        summary.setFourStarCount(count);
        summary.setFiveStarCount(0);
        reviewSummaryRepository.save(summary);
    }

    private GuideSummaryResponse guideView() {
        return guideService.getGuideById(guide.getGuideId());
    }

    private CabResponse cabView() {
        return cabService.searchCabs(null, null).stream()
                .filter(c -> c.cabId().equals(cab.getCabId()))
                .findFirst()
                .orElseThrow();
    }

    /* ============================================================
     * THE BUG
     * ============================================================ */

    /**
     * The core defect. A guide with a wall of bad reviews was shown
     * as perfect because the summary was never consulted.
     */
    @Test
    @DisplayName("a badly reviewed guide shows the real rating")
    void guideShowsItsRealRating() {
        summarise(
                ReviewTargetType.GUIDE,
                guide.getGuideId(),
                "1.40",
                50
        );

        assertEquals(
                0, new BigDecimal("1.40")
                        .compareTo(guideView().rating()),
                "a guide averaging 1.40 from 50 reviews must not "
                        + "display the 5.00 its column was born with"
        );
    }

    @Test
    @DisplayName("a badly reviewed cab shows the real rating")
    void cabShowsItsRealRating() {
        summarise(
                ReviewTargetType.CAB,
                cab.getCabId(),
                "1.80",
                30
        );

        assertEquals(
                0, new BigDecimal("1.80")
                        .compareTo(cabView().rating())
        );
    }

    /**
     * Unreviewed is unknown, not perfect. The old default made
     * every new listing look flawless on day one.
     */
    @Test
    @DisplayName("an unreviewed guide shows no rating at all")
    void unreviewedGuideHasNoRating() {
        GuideSummaryResponse view = guideView();

        assertNull(
                view.rating(),
                "a number on a card reads as evidence, and there "
                        + "is none"
        );
        assertEquals(0, view.reviewCount());
    }

    @Test
    @DisplayName("an unreviewed cab shows no rating at all")
    void unreviewedCabHasNoRating() {
        CabResponse view = cabView();

        assertNull(view.rating());
        assertEquals(0, view.reviewCount());
    }

    /**
     * A summary row that exists but holds no reviews is the same
     * situation as no row, and must not surface a 0.00.
     */
    @Test
    @DisplayName("an empty summary does not read as a zero rating")
    void emptySummaryIsNotZero() {
        summarise(
                ReviewTargetType.GUIDE,
                guide.getGuideId(),
                "0.00",
                0
        );

        assertNull(guideView().rating());
    }

    /* ============================================================
     * THE COUNT
     * ============================================================ */

    /**
     * The rating alone is not enough to read. A 5.00 from one
     * review and a 5.00 from two hundred are different claims.
     */
    @Test
    @DisplayName("the rating ships with the count that backs it")
    void ratingShipsWithItsCount() {
        summarise(
                ReviewTargetType.GUIDE,
                guide.getGuideId(),
                "4.80",
                17
        );
        summarise(
                ReviewTargetType.CAB,
                cab.getCabId(),
                "3.10",
                9
        );

        assertEquals(17, guideView().reviewCount());
        assertEquals(9, cabView().reviewCount());
    }

    /**
     * Reviews are typed by target, so a guide's rating must not
     * bleed in from a cab or a hotel that happens to share an id.
     */
    @Test
    @DisplayName("a rating is only read for its own target type")
    void ratingIsScopedByTargetType() {
        summarise(
                ReviewTargetType.CAB,
                guide.getGuideId(),
                "1.00",
                10
        );

        assertNull(
                guideView().rating(),
                "a cab summary sharing this id is not evidence "
                        + "about the guide"
        );
    }
}