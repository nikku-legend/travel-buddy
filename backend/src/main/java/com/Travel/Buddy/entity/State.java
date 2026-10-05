package com.Travel.Buddy.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "states",
        indexes = {
                @Index(
                        name = "idx_states_country",
                        columnList = "country_id"
                ),
                @Index(
                        name = "idx_states_region",
                        columnList = "region_zone"
                )
        }
)
public class State {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "state_id")
    private Integer stateId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "country_id",
            nullable = false
    )
    private Country country;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "region_zone",
            nullable = false,
            length = 20
    )
    private RegionZone regionZone;

    public Integer getStateId() {
        return stateId;
    }

    public void setStateId(Integer stateId) {
        this.stateId = stateId;
    }

    public Country getCountry() {
        return country;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RegionZone getRegionZone() {
        return regionZone;
    }

    public void setRegionZone(RegionZone regionZone) {
        this.regionZone = regionZone;
    }
}