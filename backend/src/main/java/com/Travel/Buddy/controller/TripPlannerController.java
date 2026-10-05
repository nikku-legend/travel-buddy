package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.trip.AddTripPlacesRequest;
import com.Travel.Buddy.dto.trip.AddTripSelectionRequest;
import com.Travel.Buddy.dto.trip.ConfirmTripCheckoutRequest;
import com.Travel.Buddy.dto.trip.PayTripCheckoutRequest;
import com.Travel.Buddy.dto.trip.CreateTripRequest;
import com.Travel.Buddy.dto.trip.CustomiseTripSelectionRequest;
import com.Travel.Buddy.dto.trip.SwapTripSelectionRequest;
import com.Travel.Buddy.dto.trip.CitySuggestionResponse;
import com.Travel.Buddy.dto.trip.SetTripDatesRequest;
import com.Travel.Buddy.dto.trip.SetTripPreferencesRequest;
import com.Travel.Buddy.dto.trip.SetTripRouteRequest;
import com.Travel.Buddy.dto.trip.SetTripScopeRequest;
import com.Travel.Buddy.dto.trip.TripBillResponse;
import com.Travel.Buddy.dto.trip.TripCheckoutPreviewResponse;
import com.Travel.Buddy.dto.trip.TripCheckoutResponse;
import com.Travel.Buddy.dto.trip.TripDetailResponse;
import com.Travel.Buddy.dto.trip.TripReviewCardResponse.TripReviewCentreResponse;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.trip.TripBillService;
import com.Travel.Buddy.service.trip.TripCartService;
import com.Travel.Buddy.service.trip.TripPlannerInputService;
import com.Travel.Buddy.service.trip.TripRecommendationService;
import com.Travel.Buddy.service.trip.TripCheckoutService;
import com.Travel.Buddy.service.trip.TripReviewService;
import com.Travel.Buddy.service.trip.TripService;
import com.Travel.Buddy.service.trip.TripService.RouteSuggestionResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Trip Planner. (SRS 2.2 FR-14, TP-01 .. TP-10)
 *
 * <p>Routes follow the SRS frontend blueprint: a planner under
 * /trip-planner for building a trip, and the traveller's own
 * journeys under /my-trips. The two sets of routes are
 * separated deliberately, because a planner screen and an
 * active-trip screen answer very different questions from the
 * same underlying data.
 */
@RestController
@RequestMapping("/api/v1")
public class TripPlannerController {

    private final TripService tripService;
    private final TripCartService cartService;
    private final TripPlannerInputService inputService;
    private final TripRecommendationService recommendationService;
    private final TripBillService billService;
    private final TripCheckoutService checkoutService;
    private final TripReviewService reviewService;
    private final UserRepository userRepository;

    public TripPlannerController(
            TripService tripService,
            TripCartService cartService,
            TripPlannerInputService inputService,
            TripRecommendationService recommendationService,
            TripBillService billService,
            TripCheckoutService checkoutService,
            TripReviewService reviewService,
            UserRepository userRepository
    ) {
        this.tripService = tripService;
        this.cartService = cartService;
        this.inputService = inputService;
        this.recommendationService = recommendationService;
        this.billService = billService;
        this.checkoutService = checkoutService;
        this.reviewService = reviewService;
        this.userRepository = userRepository;
    }

    /* ============================================================
     * PLANNER
     * ============================================================ */

    /** TP-01: dates are the first required input. */
    @PostMapping("/trip-planner/trips")
    public ResponseEntity<TripDetailResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateTripRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                tripService.create(
                        currentUserId(authentication), request
                )
        );
    }

    @GetMapping("/trip-planner/trips")
    public ResponseEntity<List<TripDetailResponse>> mine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                tripService.myTrips(
                        currentUserId(authentication),
                        page,
                        size
                )
        );
    }

    /**
     * TP-02, TP-03: the whole route is replaced in one call so
     * the order the traveller sees is the order that is saved.
     */
    @PutMapping("/trip-planner/trips/{tripId}/route")
    public ResponseEntity<TripDetailResponse> setRoute(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody SetTripRouteRequest request
    ) {
        return ResponseEntity.ok(
                tripService.setRoute(
                        currentUserId(authentication),
                        tripId,
                        request
                )
        );
    }

    /**
     * TP-03 and section 12. Advisory only: the engine proposes an
     * order and warns about the current one, and never changes
     * either without being asked.
     */
    @GetMapping("/trip-planner/trips/{tripId}/route-suggestion")
    public ResponseEntity<RouteSuggestionResponse> suggest(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ResponseEntity.ok(
                tripService.suggestRoute(
                        currentUserId(authentication), tripId
                )
        );
    }

    @GetMapping("/trip-planner/trips/{tripId}/warnings")
    public ResponseEntity<List<String>> warnings(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ResponseEntity.ok(
                tripService.warn(
                        currentUserId(authentication), tripId
                )
        );
    }

    /** TP-04: multi-select places, and the step is skippable. */
    @PostMapping("/trip-planner/trips/{tripId}/places")
    public ResponseEntity<TripDetailResponse> addPlaces(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody AddTripPlacesRequest request
    ) {
        return ResponseEntity.ok(
                tripService.addPlaces(
                        currentUserId(authentication),
                        tripId,
                        request
                )
        );
    }

    @DeleteMapping("/trip-planner/trips/{tripId}/places/{tripPlaceId}")
    public ResponseEntity<TripDetailResponse> removePlace(
            Authentication authentication,
            @PathVariable Long tripId,
            @PathVariable Long tripPlaceId
    ) {
        return ResponseEntity.ok(
                tripService.removePlace(
                        currentUserId(authentication),
                        tripId,
                        tripPlaceId
                )
        );
    }

    /* ============================================================
     * TRIP CART
     * ============================================================ */

    /**
     * TP-05 .. TP-08. Records intent and reserves nothing; the
     * response carries {@code reserved=false} so no client can
     * present a selection as a booking.
     */
    @PostMapping("/trip-planner/trips/{tripId}/selections")
    public ResponseEntity<TripDetailResponse> addSelection(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody AddTripSelectionRequest request
    ) {
        return ResponseEntity.ok(
                cartService.addSelection(
                        currentUserId(authentication),
                        tripId,
                        request
                )
        );
    }

    @DeleteMapping("/trip-planner/trips/{tripId}/selections/{selectionId}")
    public ResponseEntity<TripDetailResponse> removeSelection(
            Authentication authentication,
            @PathVariable Long tripId,
            @PathVariable Long selectionId
    ) {
        return ResponseEntity.ok(
                cartService.removeSelection(
                        currentUserId(authentication),
                        tripId,
                        selectionId
                )
        );
    }

    /** TP-09: the itemized bill. */
    @GetMapping("/trip-planner/trips/{tripId}/bill")
    public ResponseEntity<TripBillResponse> bill(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        Long userId = currentUserId(authentication);

        /*
         * Ownership is verified before the bill is read, so a
         * guessed trip id cannot expose another cart.
         */
        Trip trip = tripService.ownedTrip(userId, tripId);

        return ResponseEntity.ok(billService.preview(trip));
    }

    /* ============================================================
     * CENTRAL CHECKOUT
     * ============================================================ */

    /**
     * TP-10. Revalidates availability and price, then returns the
     * bill and whether the traveller must explicitly confirm a
     * change. Charges nothing.
     */
    @PostMapping("/trip-planner/trips/{tripId}/checkout/preview")
    public ResponseEntity<TripCheckoutPreviewResponse> preview(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ResponseEntity.ok(
                checkoutService.preview(
                        currentUserId(authentication), tripId
                )
        );
    }

    /**
     * Section 6: the affected line item must be shown and
     * confirmed before the new total is charged. The accepted
     * total is carried explicitly so a bill that moved again
     * cannot slip through.
     */
    @PostMapping("/trip-planner/trips/{tripId}/checkout/confirm")
    public ResponseEntity<TripCheckoutResponse>
            confirm(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody ConfirmTripCheckoutRequest request
    ) {
        return ResponseEntity.ok(
                TripCheckoutResponse.of(
                        checkoutService.assertAgreed(
                                currentUserId(authentication),
                                tripId,
                                request
                        )
                )
        );
    }

    /**
     * Simulates the gateway clearing payment. (SRS 2.2 TP-10)
     *
     * <p>The trip checkout could be previewed and confirmed but
     * never paid, because nothing could mark it settled. This
     * closes that gap the same way single-booking checkout does.
     *
     * <p>Mock by name on purpose. A real gateway callback is a
     * different trust boundary and must not be reachable by
     * posting to an endpoint the browser controls.
     */
    @PostMapping(
            "/trip-planner/trips/{tripId}/checkout/pay"
    )
    public ResponseEntity<TripCheckoutResponse>
            payMock(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody PayTripCheckoutRequest request
    ) {
        return ResponseEntity.ok(
                TripCheckoutResponse.of(
                        checkoutService.payMock(
                                currentUserId(authentication),
                                tripId,
                                request.checkoutId(),
                                Boolean.TRUE.equals(
                                        request.paymentSuccessful()
                                )
                        )
                )
        );
    }
    @GetMapping("/trip-planner/trips/{tripId}/checkout")
    public ResponseEntity<List<TripCheckoutResponse>>
            history(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ResponseEntity.ok(
                checkoutService.history(
                        currentUserId(authentication), tripId
                ).stream()
                        .map(TripCheckoutResponse::of)
                        .toList()
        );
    }

    /* ============================================================
     * SRS 2.3 CUSTOM PLANNER WIZARD
     *
     * The 2.3 flow collects inputs in the order
     *   PERSONS -> ZONE -> REGION -> DATES -> BUDGET/PREMIUM
     * rather than starting with dates as 2.2 did. Each step has
     * its own endpoint so a wizard can save as it goes, and so
     * changing one step never discards the others.
     * ============================================================ */

    /** TP-02 and TP-05: party size and travel style. */
    @PostMapping("/trip-planner/trips/{tripId}/preferences")
    public ResponseEntity<TripDetailResponse> setPreferences(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody SetTripPreferencesRequest request
    ) {
        return ResponseEntity.ok(
                inputService.setPreferences(
                        currentUserId(authentication),
                        tripId,
                        request
                )
        );
    }

    /** TP-03: the first geographic constraint. */
    @PostMapping("/trip-planner/trips/{tripId}/scope")
    public ResponseEntity<TripDetailResponse> setScope(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody SetTripScopeRequest request
    ) {
        return ResponseEntity.ok(
                inputService.setScope(
                        currentUserId(authentication),
                        tripId,
                        request
                )
        );
    }

    /** TP-04: dates, validated and duration recalculated. */
    @PostMapping("/trip-planner/trips/{tripId}/dates")
    public ResponseEntity<TripDetailResponse> setDates(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody SetTripDatesRequest request
    ) {
        return ResponseEntity.ok(
                inputService.setDates(
                        currentUserId(authentication),
                        tripId,
                        request.startDate(),
                        request.endDate()
                )
        );
    }

    /**
     * TP-06: cities the engine proposes inside the chosen scope.
     * Suggests only; nothing is selected until the traveller
     * commits a route, per section 4.1.
     */
    @GetMapping("/trip-planner/trips/{tripId}/city-suggestions")
    public ResponseEntity<List<CitySuggestionResponse>> citySuggestions(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ResponseEntity.ok(
                inputService.suggestCities(
                        currentUserId(authentication), tripId
                )
        );
    }

    /**
     * TP-08: hotel recommendations for every city stop.
     *
     * <p>Recomputes rather than appends, so a traveller who
     * changes dates or places sees the current answer instead of
     * a mix of old and new. Books nothing.
     */
    @PostMapping("/trip-planner/trips/{tripId}/hotel-recommendations")
    public ResponseEntity<TripDetailResponse> recommendHotels(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        Long userId = currentUserId(authentication);

        recommendationService.recommendHotels(userId, tripId);

        return ResponseEntity.ok(
                tripService.get(userId, tripId)
        );
    }

    /* ============================================================
     * 6.2  HOTEL CUSTOMISATION
     *
     * Change room type, room count or guests, or swap the
     * selection outright. None of these accept dates: section 6.2
     * requires dates to move through the planner so the rest of
     * the itinerary is revalidated.
     * ============================================================ */

    @PatchMapping(
            "/trip-planner/trips/{tripId}/selections/{selectionId}"
    )
    public ResponseEntity<TripDetailResponse> customise(
            Authentication authentication,
            @PathVariable Long tripId,
            @PathVariable Long selectionId,
            @Valid @RequestBody CustomiseTripSelectionRequest request
    ) {
        return ResponseEntity.ok(
                cartService.customiseSelection(
                        currentUserId(authentication),
                        tripId,
                        selectionId,
                        request
                )
        );
    }

    /**
     * "Replace recommended hotel." The old selection is retained
     * as removed rather than deleted, and the rest of the trip is
     * untouched.
     */
    @PutMapping(
            "/trip-planner/trips/{tripId}/selections/{selectionId}"
    )
    public ResponseEntity<TripDetailResponse> swap(
            Authentication authentication,
            @PathVariable Long tripId,
            @PathVariable Long selectionId,
            @Valid @RequestBody SwapTripSelectionRequest request
    ) {
        return ResponseEntity.ok(
                cartService.swapSelection(
                        currentUserId(authentication),
                        tripId,
                        selectionId,
                        request
                )
        );
    }

    /* ============================================================
     * MY TRIPS  (the treasure-map journey view)
     * ============================================================ */

    @GetMapping("/my-trips/{tripId}")
    public ResponseEntity<TripDetailResponse> trip(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ResponseEntity.ok(
                tripService.get(
                        currentUserId(authentication), tripId
                )
        );
    }

    /**
     * TP-12: the post-trip Review Center.
     *
     * <p>Returns one card per service that has completed and can be
     * reviewed, together with counts so the UI can show "2 of 3"
     * without a second request.
     *
     * <p>Safe to call repeatedly. The window is re-derived on every
     * read, which is what lets a card appear the moment a partner
     * checks a guest out, without a scheduled job that has to guess
     * when services complete.
     */
    @GetMapping("/my-trips/{tripId}/reviews")
    public ResponseEntity<TripReviewCentreResponse> reviewCentre(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ResponseEntity.ok(
                reviewService.reviewCentre(
                        currentUserId(authentication), tripId
                )
        );
    }

    /**
     * How many services on a trip are waiting for a review.
     *
     * <p>Separate and integer-only so the My Trips list can show a
     * badge without fetching every card and every review body for
     * every trip in the list.
     */
    @GetMapping("/my-trips/{tripId}/reviews/pending-count")
    public ResponseEntity<Map<String, Integer>> pendingReviews(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ResponseEntity.ok(
                Map.of(
                        "pending",
                        reviewService.pendingCount(
                                currentUserId(authentication), tripId
                        )
                )
        );
    }

    /* ============================================================
     * HELPERS
     * ============================================================ */

    private Long currentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "Authentication is required"
            );
        }
        return userRepository.findByEmail(
                        authentication.getName()
                )
                .map(User::getUserId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: "
                                + authentication.getName()
                ));
    }
}