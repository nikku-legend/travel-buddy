package com.Travel.Buddy.dto.partner;

import com.Travel.Buddy.entity.KycDocumentStatus;

import java.time.LocalDateTime;

/**
 * A KYC document slot plus whatever has been uploaded against it.
 *
 * <p>Every mandatory document is returned even when nothing has been
 * uploaded yet, so the UI can render a checklist and block submission
 * rather than discovering a missing document at submit time.
 */
public record KycDocumentResponse(
        Long documentId,
        String documentCode,
        String displayName,
        boolean mandatory,
        KycDocumentStatus status,
        String fileName,
        String contentType,
        Long fileSizeBytes,
        LocalDateTime uploadedAt,
        LocalDateTime verifiedAt,
        String rejectionReason
) {

    public boolean hasFile() {
        return fileName != null && !fileName.isBlank();
    }
}