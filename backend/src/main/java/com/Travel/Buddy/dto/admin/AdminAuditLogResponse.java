package com.Travel.Buddy.dto.admin;

import java.time.LocalDateTime;

/**
 * One immutable audit entry as the console reads it. (FR-30)
 */
public record AdminAuditLogResponse(

        Long auditId,

        String action,

        String entityType,

        Long entityId,

        String detail,

        String actorName,

        String actorEmail,

        String ipAddress,

        LocalDateTime createdAt
) {
}