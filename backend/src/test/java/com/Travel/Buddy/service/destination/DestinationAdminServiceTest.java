package com.Travel.Buddy.service.destination;

import com.Travel.Buddy.dto.admin.AdminDestinationRequest;
import com.Travel.Buddy.dto.admin.AdminDestinationResponse;
import com.Travel.Buddy.entity.State;
import com.Travel.Buddy.entity.TouristPlace;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.CityRepository;
import com.Travel.Buddy.repository.StateRepository;
import com.Travel.Buddy.repository.TouristPlaceRepository;
import com.Travel.Buddy.service.admin.AdminAuditService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Destination admin tools (FR-32): creation requires a name and
 * a state, updates are partial, and both paths are audited.
 */
class DestinationAdminServiceTest {

    private final TouristPlaceRepository placeRepository =
            mock(TouristPlaceRepository.class);
    private final StateRepository stateRepository =
            mock(StateRepository.class);
    private final CityRepository cityRepository =
            mock(CityRepository.class);
    private final AdminAuditService auditService =
            mock(AdminAuditService.class);

    private DestinationAdminService service;
    private State state;

    @BeforeEach
    void setUp() {
        service = new DestinationAdminService(
                placeRepository,
                stateRepository,
                cityRepository,
                auditService
        );
        state = new State();
    }

    private AdminDestinationRequest request(
            Long stateId,
            String name
    ) {
        return new AdminDestinationRequest(
                stateId,
                null,
                name,
                "Nature",
                "Backwater cruise",
                new BigDecimal("50.00"),
                "INR",
                null,
                null,
                null,
                false,
                true
        );
    }

    @Test
    void createRejectsAMissingName() {
        PartnerApplicationException exception = assertThrows(
                PartnerApplicationException.class,
                () -> service.create(7L, request(1L, "   "))
        );

        assertEquals(400, exception.getStatus().value());
        verifyNoInteractions(auditService);
    }

    @Test
    void createRejectsAnUnknownState() {
        when(stateRepository.findById(9))
                .thenReturn(Optional.empty());

        PartnerApplicationException exception = assertThrows(
                PartnerApplicationException.class,
                () -> service.create(7L, request(9L, "Munnar"))
        );

        assertEquals(404, exception.getStatus().value());
        verifyNoInteractions(auditService);
    }

    @Test
    void createActivatesByDefaultAndAudits() {
        when(stateRepository.findById(1))
                .thenReturn(Optional.of(state));
        when(placeRepository.save(any(TouristPlace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminDestinationResponse response =
                service.create(7L, request(1L, "Alleppey"));

        assertEquals("Alleppey", response.name());
        assertTrue(response.active());
        assertFalse(response.featured());
        verify(auditService).record(
                eq(7L),
                eq("DESTINATION_CREATED"),
                eq("TouristPlace"),
                isNull(),
                eq("Alleppey")
        );
    }

    @Test
    void updateLeavesUnmentionedFieldsUntouched() {
        TouristPlace place = new TouristPlace();
        place.setPlaceId(42L);
        place.setName("Existing");
        place.setCategory("Heritage");
        place.setEntryFee(new BigDecimal("25.00"));
        place.setActive(true);

        when(placeRepository.findById(42L))
                .thenReturn(Optional.of(place));
        when(placeRepository.save(any(TouristPlace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminDestinationRequest patch = new AdminDestinationRequest(
                null, null, null, null, null, null,
                null, null, null, null, null, false
        );

        AdminDestinationResponse response =
                service.update(7L, 42L, patch);

        assertEquals("Existing", response.name());
        assertEquals("Heritage", response.category());
        assertEquals(
                0,
                new BigDecimal("25.00").compareTo(response.entryFee())
        );
        assertFalse(response.active());
        verify(auditService).record(
                eq(7L),
                eq("DESTINATION_UPDATED"),
                eq("TouristPlace"),
                eq(42L),
                startsWith("Existing")
        );
    }

    @Test
    void updateOfAMissingDestinationIsA404() {
        when(placeRepository.findById(404L))
                .thenReturn(Optional.empty());

        PartnerApplicationException exception = assertThrows(
                PartnerApplicationException.class,
                () -> service.update(
                        7L,
                        404L,
                        new AdminDestinationRequest(
                                null, null, "New", null, null, null,
                                null, null, null, null, null, null
                        )
                )
        );

        assertEquals(404, exception.getStatus().value());
        verifyNoInteractions(auditService);
    }

    @Test
    void listReturnsEveryListingIncludingRetiredOnes() {
        TouristPlace retired = new TouristPlace();
        retired.setPlaceId(1L);
        retired.setName("Closed Fort");
        retired.setActive(false);

        when(placeRepository.findAll(any(
                org.springframework.data.domain.Sort.class
        )))
                .thenReturn(List.of(retired));

        List<AdminDestinationResponse> destinations = service.list();

        assertEquals(1, destinations.size());
        assertFalse(destinations.get(0).active());
        assertEquals("Closed Fort", destinations.get(0).name());
    }
}