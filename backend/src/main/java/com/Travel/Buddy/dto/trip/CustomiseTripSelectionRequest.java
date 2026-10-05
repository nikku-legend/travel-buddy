package com.Travel.Buddy.dto.trip;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Hotel customisation. (SRS 2.3 section 6.2)
 *
 * <p>Deliberately carries no dates. Section 6.2 says dates change
 * "only through the planner so the rest of the itinerary can be
 * revalidated": moving a stay in isolation would leave the
 * transport and guide legs of the same trip pointing at the old
 * dates, and the traveller would find out at checkout.
 */
public record CustomiseTripSelectionRequest(

        /* A different room type within the same property. */
        Long roomTypeId,

        /*
         * "Change number of rooms where supported." At least one,
         * and checked against availability before it is accepted.
         */
        @Min(value = 1, message = "At least one room")
        Integer rooms,

        /*
         * How many people the stay is for. Kept separate from
         * rooms because occupancy and room count are different
         * questions: four people in one large room is one room.
         */
        @Min(value = 1, message = "At least one guest")
        Integer guests
) {
}