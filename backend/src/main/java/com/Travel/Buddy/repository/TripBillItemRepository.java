package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.TripBillItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripBillItemRepository
        extends JpaRepository<TripBillItem, Long> {

    List<TripBillItem> findByCheckout_CheckoutIdOrderByBillItemIdAsc(
            Long checkoutId
    );
}