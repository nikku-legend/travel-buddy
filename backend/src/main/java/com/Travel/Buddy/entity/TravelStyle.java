package com.Travel.Buddy.entity;

/**
 * Planning mode. (SRS 2.3 section 3.5, TP-05)
 *
 * <p>Two modes only. The SRS is explicit that Budget Travel is not
 * "cheapest wins" and Premium Travel is not "ignore the budget",
 * so both are encoded as a ranking preference rather than as a
 * filter:
 *
 * <ul>
 *   <li><strong>BUDGET</strong> weights price, while still
 *       applying the minimum quality and trust rules. A
 *       non-existent or unverified property is never a budget
 *       option.</li>
 *   <li><strong>PREMIUM</strong> weights rating, amenities and
 *       location, while remaining availability- and
 *       budget-aware. Premium does not mean unlimited.</li>
 * </ul>
 */
public enum TravelStyle {

    BUDGET,

    PREMIUM;

    /**
     * How strongly price counts against quality when ranking
     * hotels. Budget leans on price, premium leans on rating and
     * amenities, but neither reaches zero: a premium traveller
     * still wants the cheaper of two identical rooms.
     */
    public double priceWeight() {
        return this == BUDGET ? 0.55 : 0.25;
    }

    public double qualityWeight() {
        return this == BUDGET ? 0.45 : 0.75;
    }
}