package com.Travel.Buddy.service.geo;

import com.Travel.Buddy.dto.geo.CountryResponse;
import com.Travel.Buddy.dto.geo.StateResponse;
import com.Travel.Buddy.entity.Country;
import com.Travel.Buddy.entity.State;
import com.Travel.Buddy.entity.RegionZone;
import com.Travel.Buddy.repository.CountryRepository;
import com.Travel.Buddy.repository.StateRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeoService {

    private final CountryRepository countryRepository;
    private final StateRepository stateRepository;

    public GeoService(
            CountryRepository countryRepository,
            StateRepository stateRepository
    ) {
        this.countryRepository = countryRepository;
        this.stateRepository = stateRepository;
    }

    /**
     * Get all active countries.
     */
    public List<CountryResponse> getCountries() {

        List<Country> countries =
                countryRepository.findAllByOrderByNameAsc();

        return countries.stream()
                .map(this::toCountryResponse)
                .toList();
    }

    /**
     * Get states belonging to a country.
     *
     * If region is supplied, only states from that region
     * are returned.
     */
    public List<StateResponse> getStates(
            Integer countryId,
            RegionZone region
    ) {

        List<State> states;

        if (region == null) {

            states =
                    stateRepository
                            .findByCountry_CountryIdOrderByNameAsc(
                                    countryId
                            );

        } else {

            states =
                    stateRepository
                            .findByCountry_CountryIdAndRegionZoneOrderByNameAsc(
                                    countryId,
                                    region
                            );
        }

        return states.stream()
                .map(this::toStateResponse)
                .toList();
    }

    /**
     * Convert Country entity to CountryResponse.
     */
    private CountryResponse toCountryResponse(
            Country country
    ) {

        return new CountryResponse(
                country.getCountryId(),
                country.getName(),
                country.getIsoCode()
        );
    }

    /**
     * Convert State entity to StateResponse.
     */
    private StateResponse toStateResponse(
            State state
    ) {

        return new StateResponse(
                state.getStateId(),
                state.getName(),
                state.getRegionZone() != null ? state.getRegionZone().name() : null
        );
    }
}
