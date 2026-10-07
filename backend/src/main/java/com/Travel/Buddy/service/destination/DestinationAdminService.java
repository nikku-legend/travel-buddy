package com.Travel.Buddy.service.destination;

import com.Travel.Buddy.dto.admin.AdminDestinationRequest;
import com.Travel.Buddy.dto.admin.AdminDestinationResponse;
import com.Travel.Buddy.entity.City;
import com.Travel.Buddy.entity.State;
import com.Travel.Buddy.entity.TouristPlace;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.CityRepository;
import com.Travel.Buddy.repository.StateRepository;
import com.Travel.Buddy.repository.TouristPlaceRepository;
import com.Travel.Buddy.service.admin.AdminAuditService;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Destination admin tools. (FR-32)
 *
 * <p>The public endpoints only ever see active places; this
 * service is the one place the whole catalogue -- retired
 * included -- is visible and editable, because a destination
 * you cannot switch off is a destination you cannot take back
 * from the front page.
 */
@Service
public class DestinationAdminService {

    private final TouristPlaceRepository placeRepository;
    private final StateRepository stateRepository;
    private final CityRepository cityRepository;
    private final AdminAuditService auditService;

    public DestinationAdminService(
            TouristPlaceRepository placeRepository,
            StateRepository stateRepository,
            CityRepository cityRepository,
            AdminAuditService auditService
    ) {
        this.placeRepository = placeRepository;
        this.stateRepository = stateRepository;
        this.cityRepository = cityRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AdminDestinationResponse> list() {
        return placeRepository
                .findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminDestinationResponse create(
            Long actorUserId,
            AdminDestinationRequest request
    ) {
        if (isBlank(request.name())) {
            throw PartnerApplicationException.badRequest(
                    "A name is required"
            );
        }

        if (request.stateId() == null) {
            throw PartnerApplicationException.badRequest(
                    "A state is required"
            );
        }

        State state = resolveState(request.stateId());

        TouristPlace place = new TouristPlace();
        place.setState(state);
        place.setCity(resolveCity(request.cityId()));
        place.setName(request.name().trim());
        place.setCurrency(normalizeCurrency(request.currency()));
        applyOptionalFields(place, request);
        place.setFeatured(Boolean.TRUE.equals(request.featured()));
        place.setActive(
                request.active() == null || request.active()
        );

        place = placeRepository.save(place);

        auditService.record(
                actorUserId,
                "DESTINATION_CREATED",
                "TouristPlace",
                place.getPlaceId(),
                place.getName()
        );

        return toResponse(place);
    }

    /**
     * Null fields in the request are left untouched, so the
     * console can save one column without resending the row.
     */
    @Transactional
    public AdminDestinationResponse update(
            Long actorUserId,
            Long placeId,
            AdminDestinationRequest request
    ) {
        TouristPlace place = placeRepository
                .findById(placeId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Destination not found"
                        )
                );

        if (request.stateId() != null) {
            place.setState(resolveState(request.stateId()));
        }

        if (request.cityId() != null) {
            place.setCity(resolveCity(request.cityId()));
        }

        if (!isBlank(request.name())) {
            place.setName(request.name().trim());
        }

        if (request.currency() != null) {
            place.setCurrency(
                    normalizeCurrency(request.currency())
            );
        }

        applyOptionalFields(place, request);

        if (request.featured() != null) {
            place.setFeatured(request.featured());
        }

        if (request.active() != null) {
            place.setActive(request.active());
        }

        place = placeRepository.save(place);

        auditService.record(
                actorUserId,
                "DESTINATION_UPDATED",
                "TouristPlace",
                place.getPlaceId(),
                place.getName() + " (active="
                        + place.getActive()
                        + ", featured=" + place.isFeatured()
                        + ")"
        );

        return toResponse(place);
    }

    private void applyOptionalFields(
            TouristPlace place,
            AdminDestinationRequest request
    ) {
        if (request.category() != null) {
            place.setCategory(request.category());
        }
        if (request.description() != null) {
            place.setDescription(request.description());
        }
        if (request.entryFee() != null) {
            place.setEntryFee(request.entryFee());
        }
        if (request.imageUrl() != null) {
            place.setImageUrl(request.imageUrl());
        }
        if (request.latitude() != null) {
            place.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            place.setLongitude(request.longitude());
        }
    }

    private City resolveCity(Long cityId) {
        if (cityId == null) {
            return null;
        }

        return cityRepository
                .findById(cityId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "City not found"
                        )
                );
    }

    private State resolveState(Long stateId) {
        int repositoryId;
        try {
            repositoryId = Math.toIntExact(stateId);
        } catch (ArithmeticException exception) {
            throw PartnerApplicationException.badRequest(
                    "State id is outside the supported range"
            );
        }

        return stateRepository
                .findById(repositoryId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "State not found"
                        )
                );
    }

    private static String normalizeCurrency(String currency) {
        if (isBlank(currency) || currency.trim().length() != 3) {
            throw PartnerApplicationException.badRequest(
                    "A three-letter currency code is required"
            );
        }

        return currency.trim().toUpperCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private AdminDestinationResponse toResponse(
            TouristPlace place
    ) {
        State state = place.getState();
        City city = place.getCity();

        return new AdminDestinationResponse(
                place.getPlaceId(),
                state == null || state.getStateId() == null
                        ? null
                        : state.getStateId().longValue(),
                state == null ? null : state.getName(),
                city == null ? null : city.getCityId(),
                city == null ? null : city.getName(),
                place.getName(),
                place.getCategory(),
                place.getDescription(),
                place.getEntryFee(),
                place.getCurrency(),
                place.getImageUrl(),
                place.getLatitude(),
                place.getLongitude(),
                place.isFeatured(),
                place.getActive()
        );
    }
}