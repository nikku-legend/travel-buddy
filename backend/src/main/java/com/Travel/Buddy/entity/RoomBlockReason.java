package com.Travel.Buddy.entity;

/**
 * Why a partner took rooms out of service. (FR-21)
 *
 * <p>{@code PRIVATE_USE} and {@code OWNER_USE} are separate from
 * {@code MAINTENANCE} on purpose: the first two are commercial
 * decisions, the third is an operational cost, and settlement and
 * commission reporting treat them differently.
 */
public enum RoomBlockReason {

    MAINTENANCE,

    PRIVATE_USE,

    TEMPORARY_CLOSURE,

    DEEP_CLEANING,

    OWNER_USE,

    OTHER
}