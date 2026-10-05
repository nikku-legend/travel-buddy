package com.Travel.Buddy.service.trip;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Great-circle distance. (SRS 2.2 section 4)
 *
 * <p>Two planner features depend on this and nothing else:
 * city sequence intelligence (TP-03) and "2.1 km from your
 * selected place" hotel proximity (TP-05). Both are domestic-scale
 * distances, where the haversine formula is accurate to well
 * under a percent and far cheaper than a geodesic library.
 *
 * <p>No external map service is called. Recommendations must work
 * offline and must not leak a traveller's planned route to a
 * third party, and for city-to-city and hotel-to-attraction
 * distances the coordinates already in the database are enough.
 */
@Service
public class GeoDistanceService {

    /**
     * Mean Earth radius in kilometres (IUGG).
     */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    /**
     * Below this, two points are treated as the same place.
     *
     * <p>Coordinates are stored to 7 decimal places, roughly 1cm,
     * so sub-metre differences are noise from a traveller tapping
     * a map rather than a real distance worth ranking on.
     */
    private static final double SAME_PLACE_KM = 0.001;

    /**
     * @return great-circle distance in kilometres, or null when
     * either point is missing a coordinate
     */
    public Double distanceKm(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        /*
         * min(1, ...) guards against a value marginally above 1
         * from floating point error, which would make the
         * asin() return NaN for antipodal points.
         */
        double c = 2 * Math.asin(Math.sqrt(Math.min(1.0, a)));

        return EARTH_RADIUS_KM * c;
    }

    public BigDecimal distanceKm(
            BigDecimal lat1,
            BigDecimal lon1,
            BigDecimal lat2,
            BigDecimal lon2
    ) {
        if (lat1 == null || lon1 == null
                || lat2 == null || lon2 == null) {
            return null;
        }

        double km = distanceKm(
                lat1.doubleValue(), lon1.doubleValue(),
                lat2.doubleValue(), lon2.doubleValue()
        );

        return BigDecimal.valueOf(km).setScale(
                2, RoundingMode.HALF_UP
        );
    }

    /**
     * Nearest of a set of reference points.
     *
     * <p>Returns the distance to the closest reference, which is
     * what "how far is this hotel from my trip" actually means.
     * A hotel is near a trip if it is near <em>any</em> of the
     * selected places, not near all of them.
     *
     * @return the smallest distance, or null when no reference
     * point has coordinates
     */
    public BigDecimal nearestDistanceKm(
            double lat,
            double lon,
            java.util.List<double[]> referencePoints
    ) {
        if (referencePoints == null
                || referencePoints.isEmpty()) {
            return null;
        }

        Double nearest = null;

        for (double[] point : referencePoints) {
            if (point == null || point.length < 2) {
                continue;
            }

            double d = distanceKm(
                    lat, lon, point[0], point[1]
            );

            if (nearest == null || d < nearest) {
                nearest = d;
            }
        }

        if (nearest == null) {
            return null;
        }

        return BigDecimal.valueOf(nearest).setScale(
                2, RoundingMode.HALF_UP
        );
    }

    /**
     * Human-readable justification for a proximity claim.
     *
     * <p>Section 4.1 asks for exactly this wording. Formatting it
     * in one place stops the number and the sentence drifting
     * apart, which would show a traveller "1.2 km" beside a
     * sentence saying "2.1 km".
     */
    public String describeDistance(BigDecimal km) {
        if (km == null) {
            return null;
        }

        double value = km.doubleValue();

        if (value < SAME_PLACE_KM) {
            return "at your selected place";
        }

        if (value < 1.0) {
            int metres = (int) Math.round(value * 1000);
            return metres + " m from your selected place";
        }

        return km.setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
                + " km from your selected place";
    }
}