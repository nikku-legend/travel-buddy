package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.TripBillResponse;
import com.Travel.Buddy.dto.trip.TripHealthResponse;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripCity;
import com.Travel.Buddy.entity.TripSelection;
import com.Travel.Buddy.entity.TripSelectionStatus;
import com.Travel.Buddy.repository.TripCityRepository;
import com.Travel.Buddy.repository.TripSelectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class TripHealthService {

    private final TripService tripService;
    private final TripCityRepository tripCityRepository;
    private final TripSelectionRepository selectionRepository;
    private final TripBillService billService;

    public TripHealthService(
            TripService tripService,
            TripCityRepository tripCityRepository,
            TripSelectionRepository selectionRepository,
            TripBillService billService
    ) {
        this.tripService = tripService;
        this.tripCityRepository = tripCityRepository;
        this.selectionRepository = selectionRepository;
        this.billService = billService;
    }

    /**
     * Reports only checks that can be derived from saved trip data.
     * It does not reprice, reserve inventory, or alter the route.
     */
    @Transactional(readOnly = true)
    public TripHealthResponse inspect(Long userId, Long tripId) {
        Trip trip = tripService.requireOwned(userId, tripId);
        List<TripCity> stops =
                tripCityRepository.findByTrip_TripIdOrderBySequenceAsc(
                        tripId
                );
        List<TripSelection> selections =
                selectionRepository.findByTrip_TripIdOrderBySelectionIdAsc(
                        tripId
                );
        TripBillResponse bill = billService.preview(trip);
        List<TripHealthResponse.Issue> issues = new ArrayList<>();

        if (stops.isEmpty()) {
            issues.add(issue(
                    "ROUTE_EMPTY", "INFO",
                    "Add city stops to complete your itinerary checks.",
                    null, null
            ));
        } else {
            inspectStops(trip, stops, issues);
        }

        inspectSelections(stops, selections, issues);

        BigDecimal budget = trip.getBudgetAmount();
        BigDecimal total = bill.total();
        if (budget != null
                && trip.getCurrency().equalsIgnoreCase(
                trip.getBudgetCurrency()
        )
                && total.compareTo(budget) > 0) {
            issues.add(issue(
                    "OVER_BUDGET", "WARNING",
                    "Your current estimate is " + total + " "
                            + trip.getCurrency() + ", above your budget of "
                            + budget + " " + trip.getBudgetCurrency() + ".",
                    null, null
            ));
        }

        List<String> routeWarnings = tripService.warn(userId, tripId);
        for (String warning : routeWarnings) {
            boolean alreadyCovered = issues.stream().anyMatch(existing ->
                    warning.contains("budget")
                            && "OVER_BUDGET".equals(existing.code())
                            || warning.contains("overlap")
                            && "CITY_DATE_CONFLICT".equals(existing.code())
            );
            if (!alreadyCovered) {
                issues.add(issue(
                        "ROUTE_ADVICE", "WARNING",
                        warning, null, null
                ));
            }
        }

        String status = issues.stream().anyMatch(i ->
                "WARNING".equals(i.severity())
                        || "BLOCKER".equals(i.severity())
        ) ? "NEEDS_ATTENTION" : "ON_TRACK";

        return new TripHealthResponse(
                true,
                status,
                budget,
                trip.getBudgetCurrency(),
                total,
                trip.getCurrency(),
                List.copyOf(issues)
        );
    }

    private void inspectStops(
            Trip trip,
            List<TripCity> stops,
            List<TripHealthResponse.Issue> issues
    ) {
        for (TripCity stop : stops) {
            if (stop.getArrivalDate() == null
                    || stop.getDepartureDate() == null) {
                issues.add(issue(
                        "DATES_NOT_ASSIGNED", "WARNING",
                        stop.getCity().getName()
                                + " does not have stay dates assigned.",
                        stop.getTripCityId(), null
                ));
                continue;
            }
            if (stop.getArrivalDate().isBefore(trip.getStartDate())
                    || stop.getDepartureDate().isAfter(trip.getEndDate())) {
                issues.add(issue(
                        "DATES_OUTSIDE_TRIP", "BLOCKER",
                        stop.getCity().getName()
                                + " has dates outside the trip range.",
                        stop.getTripCityId(), null
                ));
            }
            if (!stop.getDepartureDate().isAfter(stop.getArrivalDate())) {
                issues.add(issue(
                        "ZERO_NIGHT_STOP", "WARNING",
                        stop.getCity().getName()
                                + " has no overnight stay assigned.",
                        stop.getTripCityId(), null
                ));
            }
        }

        for (int index = 0; index < stops.size() - 1; index++) {
            TripCity first = stops.get(index);
            TripCity next = stops.get(index + 1);
            if (first.getDepartureDate() == null
                    || next.getArrivalDate() == null) {
                continue;
            }

            long days = ChronoUnit.DAYS.between(
                    first.getDepartureDate(), next.getArrivalDate()
            );
            if (days < 0) {
                issues.add(issue(
                        "CITY_DATE_CONFLICT", "BLOCKER",
                        first.getCity().getName() + " and "
                                + next.getCity().getName()
                                + " overlap by " + Math.abs(days)
                                + " day(s).",
                        next.getTripCityId(), null
                ));
            } else if (days > 0) {
                issues.add(issue(
                        "UNPLANNED_GAP", "WARNING",
                        "There are " + days + " unallocated day(s) between "
                                + first.getCity().getName() + " and "
                                + next.getCity().getName() + ".",
                        next.getTripCityId(), null
                ));
            }
        }
    }

    private void inspectSelections(
            List<TripCity> stops,
            List<TripSelection> selections,
            List<TripHealthResponse.Issue> issues
    ) {
        for (TripSelection selection : selections) {
            if (selection.getStatus() == TripSelectionStatus.UNAVAILABLE) {
                issues.add(issue(
                        "SELECTION_UNAVAILABLE", "BLOCKER",
                        selection.getUnavailabilityReason() == null
                                ? "A selected trip service is unavailable."
                                : selection.getUnavailabilityReason(),
                        selection.getTripCity() == null
                                ? null
                                : selection.getTripCity().getTripCityId(),
                        selection.getSelectionId()
                ));
                continue;
            }

            if (selection.getSelectionType()
                    != com.Travel.Buddy.entity.TripSelectionType.HOTEL
                    || selection.getTripCity() == null
                    || selection.getCheckIn() == null
                    || selection.getCheckOut() == null) {
                continue;
            }

            TripCity stop = selection.getTripCity();
            if (stop.getArrivalDate() != null
                    && stop.getDepartureDate() != null
                    && (selection.getCheckIn().isBefore(stop.getArrivalDate())
                    || selection.getCheckOut()
                    .isAfter(stop.getDepartureDate()))) {
                issues.add(issue(
                        "STAY_DATE_CONFLICT", "BLOCKER",
                        "A selected stay does not fit the saved dates for "
                                + stop.getCity().getName() + ".",
                        stop.getTripCityId(),
                        selection.getSelectionId()
                ));
            }
        }
    }

    private TripHealthResponse.Issue issue(
            String code,
            String severity,
            String message,
            Long tripCityId,
            Long selectionId
    ) {
        return new TripHealthResponse.Issue(
                code, severity, message, tripCityId, selectionId
        );
    }
}
