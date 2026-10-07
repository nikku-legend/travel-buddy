package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.TripDayAllocationResponse;
import com.Travel.Buddy.entity.City;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripCity;
import com.Travel.Buddy.entity.TripPlace;
import com.Travel.Buddy.repository.TripCityRepository;
import com.Travel.Buddy.repository.TripPlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripDayAllocationServiceTest {

    private static final LocalDate START = LocalDate.of(2027, 5, 1);

    private TripService tripService;
    private TripCityRepository tripCityRepository;
    private TripPlaceRepository tripPlaceRepository;
    private TripDayAllocationService service;

    @BeforeEach
    void setUp() {
        tripService = mock(TripService.class);
        tripCityRepository = mock(TripCityRepository.class);
        tripPlaceRepository = mock(TripPlaceRepository.class);
        service = new TripDayAllocationService(
                tripService, tripCityRepository, tripPlaceRepository
        );
    }

    @Test
    void allocatesAtLeastOneNightAndWeightsRemainingNightsBySelectedPlaces() {
        Trip trip = trip(7);
        TripCity alpha = stop(trip, "Alpha", 1);
        TripCity beta = stop(trip, "Beta", 2);
        when(tripService.requireOwned(11L, 22L)).thenReturn(trip);
        when(tripCityRepository.findByTrip_TripIdOrderBySequenceAsc(22L))
                .thenReturn(List.of(alpha, beta));
        when(tripPlaceRepository
                .findByTrip_TripIdOrderByTripPlaceIdAsc(22L))
                .thenReturn(List.of(
                        place(alpha), place(alpha), place(alpha), place(beta)
                ));

        TripDayAllocationResponse response = service.recommend(11L, 22L);

        assertTrue(response.advisoryOnly());
        assertEquals(5, response.cityAllocations().get(0).suggestedNights());
        assertEquals(2, response.cityAllocations().get(1).suggestedNights());
        assertEquals(START, response.cityAllocations().get(0).suggestedArrivalDate());
        assertEquals(START.plusDays(5),
                response.cityAllocations().get(1).suggestedArrivalDate());
        assertEquals(trip.getEndDate(),
                response.cityAllocations().get(1).suggestedDepartureDate());
        assertTrue(response.notes().isEmpty());
    }

    @Test
    void warnsWhenThereAreFewerNightsThanStopsWithoutMutatingItinerary() {
        Trip trip = trip(2);
        TripCity alpha = stop(trip, "Alpha", 1);
        TripCity beta = stop(trip, "Beta", 2);
        TripCity gamma = stop(trip, "Gamma", 3);
        alpha.stayDates(START, START.plusDays(1));
        when(tripService.requireOwned(11L, 22L)).thenReturn(trip);
        when(tripCityRepository.findByTrip_TripIdOrderBySequenceAsc(22L))
                .thenReturn(List.of(alpha, beta, gamma));
        when(tripPlaceRepository
                .findByTrip_TripIdOrderByTripPlaceIdAsc(22L))
                .thenReturn(List.of(place(gamma), place(gamma)));

        TripDayAllocationResponse response = service.recommend(11L, 22L);

        assertEquals(2, response.cityAllocations().stream()
                .mapToInt(TripDayAllocationResponse.CityAllocation::suggestedNights)
                .sum());
        assertTrue(response.cityAllocations().stream()
                .anyMatch(city -> city.suggestedNights() == 0));
        assertFalse(response.notes().isEmpty());
        assertEquals(START.plusDays(1), alpha.getDepartureDate());
    }

    private Trip trip(int nights) {
        Trip trip = new Trip(
                null, "Allocation trip", START, START.plusDays(nights),
                2, null, "INR"
        );
        return trip;
    }

    private TripCity stop(Trip trip, String name, int sequence) {
        City city = mock(City.class);
        when(city.getCityId()).thenReturn((long) sequence);
        when(city.getName()).thenReturn(name);
        TripCity stop = new TripCity(trip, city, sequence);
        ReflectionTestUtils.setField(stop, "tripCityId", (long) sequence);
        return stop;
    }

    private TripPlace place(TripCity stop) {
        return new TripPlace(null, stop, null);
    }
}
