package com.Travel.Buddy.entity;

/**
 * What kind of thing the engine is recommending. (SRS 2.2)
 *
 * <p>Separate from {@link TripSelectionType} because a
 * recommendation can only ever be a bookable service. Activities
 * are selected, never recommended, since the engine has no basis
 * for proposing an attraction a traveller did not already pick.
 */
public enum TripRecommendationType {

    HOTEL,

    GUIDE,

    CAB
}