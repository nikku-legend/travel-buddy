package com.Travel.Buddy.dto.property;

import com.Travel.Buddy.entity.PropertyStatus;
import com.Travel.Buddy.entity.PropertyType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Partner-facing property view.
 *
 * <p>Includes the approval fields and the permissions the owner is
 * allowed to take next, so the portal never has to re-derive the
 * workflow rules in the browser.
 */
public record PartnerPropertyResponse(
        Long propertyId,
        String name,
        PropertyType propertyType,
        PropertyStatus status,
        String statusLabel,
        Integer stateId,
        String stateName,
        String address,
        String description,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean isLive,
        String rejectionReason,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String reviewedByName,
        boolean canEdit,
        boolean canSubmit,
        long roomTypeCount
) {
}