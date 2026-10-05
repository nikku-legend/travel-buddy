package com.Travel.Buddy.entity;

/**
 * Operational state of a real, numbered room.
 *
 * <p>{@code OUT_OF_SERVICE} is separate from being occupied: a room
 * under renovation must not be assignable even though nobody is in
 * it, and conflating the two would hide maintenance closures from
 * the front desk.
 */
public enum PhysicalRoomStatus {

    AVAILABLE,

    OUT_OF_SERVICE
}