package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.TripDayAllocationResponse;
import com.Travel.Buddy.dto.trip.TripDayAllocationResponse.CityAllocation;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripCity;
import com.Travel.Buddy.entity.TripPlace;
import com.Travel.Buddy.repository.TripCityRepository;
import com.Travel.Buddy.repository.TripPlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a non-mutating, attraction-aware allocation of trip nights.
 */
@Service
public class TripDayAllocationService {

    private final TripService tripService;
    private final TripCityRepository tripCityRepository;
    private final TripPlaceRepository tripPlaceRepository;

    public TripDayAllocationService(
            TripService tripService,
            TripCityRepository tripCityRepository,
            TripPlaceRepository tripPlaceRepository
    ) {
        this.tripService = tripService;
        this.tripCityRepository = tripCityRepository;
        this.tripPlaceRepository = tripPlaceRepository;
    }

    @Transactional(readOnly = true)
    public TripDayAllocationResponse recommend(
            Long userId,
            Long tripId
    ) {
        Trip trip = tripService.requireOwned(userId, tripId);
        List<TripCity> stops = tripCityRepository
                .findByTrip_TripIdOrderBySequenceAsc(tripId);
        List<TripPlace> places = tripPlaceRepository
                .findByTrip_TripIdOrderByTripPlaceIdAsc(tripId);

        Map<Long, Integer> placesPerStop = new HashMap<>();
        for (TripPlace place : places) {
            Long stopId = place.getTripCity().getTripCityId();
            placesPerStop.merge(stopId, 1, Integer::sum);
        }

        int nights = Math.toIntExact(
                ChronoUnit.DAYS.between(
                        trip.getStartDate(), trip.getEndDate()
                )
        );
        int[] allocations = allocateNights(stops, placesPerStop, nights);
        List<String> notes = new ArrayList<>();
        if (stops.isEmpty()) {
            notes.add("Add city stops to get a suggested night allocation.");
        } else if (nights == 0) {
            notes.add("This trip has no overnight stays; the allocation is a day-trip outline.");
        } else if (nights < stops.size()) {
            notes.add("There are fewer nights than city stops. Some stops receive zero nights; consider shortening the route or extending the trip.");
        }

        List<CityAllocation> suggestions = new ArrayList<>();
        LocalDate arrival = trip.getStartDate();
        for (int index = 0; index < stops.size(); index++) {
            TripCity stop = stops.get(index);
            int placeCount = placesPerStop.getOrDefault(
                    stop.getTripCityId(), 0
            );
            int allocatedNights = allocations[index];
            LocalDate departure = arrival.plusDays(allocatedNights);
            suggestions.add(new CityAllocation(
                    stop.getTripCityId(),
                    stop.getCity().getCityId(),
                    stop.getCity().getName(),
                    stop.getSequence(),
                    placeCount,
                    allocatedNights,
                    arrival,
                    departure,
                    stop.getArrivalDate(),
                    stop.getDepartureDate(),
                    rationale(placeCount, allocatedNights, stops.size(), nights)
            ));
            arrival = departure;
        }

        return new TripDayAllocationResponse(
                nights, stops.size(), true, List.copyOf(notes),
                List.copyOf(suggestions)
        );
    }

    private int[] allocateNights(
            List<TripCity> stops,
            Map<Long, Integer> placesPerStop,
            int nights
    ) {
        int[] allocation = new int[stops.size()];
        if (stops.isEmpty() || nights == 0) {
            return allocation;
        }

        int remaining = nights;
        if (nights >= stops.size()) {
            for (int i = 0; i < allocation.length; i++) {
                allocation[i] = 1;
            }
            remaining -= stops.size();
        }

        int totalWeight = stops.stream()
                .mapToInt(stop -> Math.max(
                        1,
                        placesPerStop.getOrDefault(
                                stop.getTripCityId(), 0
                        )
                ))
                .sum();

        List<Remainder> remainders = new ArrayList<>();
        int distributed = 0;
        for (int i = 0; i < stops.size(); i++) {
            int weight = Math.max(1, placesPerStop.getOrDefault(
                    stops.get(i).getTripCityId(), 0
            ));
            double exactShare = (double) remaining * weight / totalWeight;
            int wholeShare = (int) exactShare;
            allocation[i] += wholeShare;
            distributed += wholeShare;
            remainders.add(new Remainder(i, exactShare - wholeShare));
        }

        int leftovers = remaining - distributed;
        remainders.sort(Comparator
                .comparingDouble(Remainder::fraction).reversed()
                .thenComparingInt(Remainder::index));
        for (int i = 0; i < leftovers; i++) {
            allocation[remainders.get(i).index()]++;
        }
        return allocation;
    }

    private String rationale(
            int selectedPlaces,
            int nights,
            int stopCount,
            int tripNights
    ) {
        if (tripNights < stopCount) {
            return selectedPlaces == 0
                    ? "No selected places; nights are distributed proportionally across the route."
                    : "Selected places influenced this stop's share, but the trip has fewer nights than stops.";
        }
        if (selectedPlaces == 0) {
            return "At least one night is reserved for this stop; remaining nights favour stops with more selected places.";
        }
        return selectedPlaces + " selected "
                + (selectedPlaces == 1 ? "place" : "places")
                + " influenced this stop's share; at least one night is reserved per city.";
    }

    private record Remainder(int index, double fraction) {
    }
}
