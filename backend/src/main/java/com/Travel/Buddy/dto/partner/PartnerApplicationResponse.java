package com.Travel.Buddy.dto.partner;

import com.Travel.Buddy.entity.PartnerApplicationStatus;
import com.Travel.Buddy.entity.PartnerType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full partner application view.
 *
 * <p>{@code canEdit} and {@code canSubmit} are computed server-side so
 * the frontend never has to re-implement the state machine and drift
 * out of sync with it.
 */
public record PartnerApplicationResponse(
        Long applicationId,
        PartnerType partnerType,
        PartnerApplicationStatus status,
        String statusLabel,

        /* Applicant */
        Long userId,
        String applicantName,
        String applicantEmail,

        /* Business information */
        String businessName,
        String contactPhone,
        String addressLine,
        String city,
        String state,
        String postalCode,
        String taxIdentifier,
        String additionalInfo,
        String bankAccountName,
        String bankAccountNumber,
        String bankIfsc,

        /* Workflow */
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String reviewedByName,
        String rejectionReason,

        /* Documents */
        List<KycDocumentResponse> documents,
        int mandatoryDocumentCount,
        int uploadedDocumentCount,

        /* Permissions */
        boolean canEdit,
        boolean canSubmit,
        boolean canWithdraw,

        /* Whether the partner role is currently active for this type */
        boolean partnerRoleActive
) {
}
