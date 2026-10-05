package com.Travel.Buddy.dto.partner;

/**
 * One required KYC document for a partner type, sourced from the
 * admin-configurable kyc_document_types catalogue.
 */
public record KycDocumentTypeResponse(
        String documentCode,
        String displayName,
        boolean mandatory,
        int displayOrder
) {
}