package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.TripCheckout;
import com.Travel.Buddy.entity.TripCheckoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripCheckoutRepository
        extends JpaRepository<TripCheckout, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select checkout from TripCheckout checkout "
            + "where checkout.checkoutId = :checkoutId")
    Optional<TripCheckout> findByIdForUpdate(
            @Param("checkoutId") Long checkoutId
    );

    List<TripCheckout> findByTrip_TripIdOrderByCheckoutIdDesc(
            Long tripId
    );

    Optional<TripCheckout> findByTrip_TripIdAndStatusIn(
            Long tripId,
            List<TripCheckoutStatus> statuses
    );

    Optional<TripCheckout> findByCheckoutReference(
            String checkoutReference
    );

    /**
     * Lookup for an idempotent payment callback. Section 6
     * requires that a repeated Razorpay callback is recognised
     * rather than treated as a second charge.
     */
    Optional<TripCheckout> findByRazorpayPaymentId(
            String razorpayPaymentId
    );

    List<TripCheckout> findByStatusOrderByCheckoutIdAsc(
            TripCheckoutStatus status
    );
}