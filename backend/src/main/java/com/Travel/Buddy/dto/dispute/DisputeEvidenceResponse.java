package com.Travel.Buddy.dto.dispute;

import com.Travel.Buddy.entity.DisputeEvidence;

import java.time.LocalDateTime;

/**
 * Metadata only. The stored path is never exposed: it is a
 * filesystem location, and leaking it would tell a claimant
 * exactly where the file lives on disk.
 */
public record DisputeEvidenceResponse(

        Long evidenceId,

        String description,

        String originalFilename,

        String contentType,

        Long fileSizeBytes,

        String uploadedByName,

        LocalDateTime createdAt
) {
    public static DisputeEvidenceResponse from(
            DisputeEvidence e
    ) {
        return new DisputeEvidenceResponse(
                e.getEvidenceId(),
                e.getDescription(),
                e.getOriginalFilename(),
                e.getContentType(),
                e.getFileSizeBytes(),
                e.getUploadedBy() == null
                        ? null
                        : e.getUploadedBy().getFullName(),
                e.getCreatedAt()
        );
    }
}