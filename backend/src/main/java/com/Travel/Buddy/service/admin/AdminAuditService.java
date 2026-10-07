package com.Travel.Buddy.service.admin;

import com.Travel.Buddy.dto.admin.AdminAuditLogResponse;
import com.Travel.Buddy.entity.AdminAuditLog;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.AdminAuditLogRepository;
import com.Travel.Buddy.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * The admin audit trail. (FR-30, SRS auditability requirement)
 *
 * <p>{@link #record} is deliberately transactional with the
 * caller rather than best-effort: the requirement is that a KYC
 * decision, a suspension or a refund approval is auditable, and
 * an audit write that can silently fail while the action stands
 * would record a partial truth. If the trail cannot be written,
 * the action does not happen.
 */
@Service
public class AdminAuditService {

    private static final int MAX_DETAIL = 1000;

    private final AdminAuditLogRepository auditRepository;
    private final UserRepository userRepository;

    public AdminAuditService(
            AdminAuditLogRepository auditRepository,
            UserRepository userRepository
    ) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
    }

    /**
     * Appends one immutable entry, joining the caller's
     * transaction so the action and its record commit together.
     */
    @Transactional
    public void record(
            Long actorUserId,
            String action,
            String entityType,
            Long entityId,
            String detail
    ) {
        User actor = userRepository
                .findById(actorUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "the acting admin account no longer exists"
                ));

        auditRepository.save(new AdminAuditLog(
                actor,
                action,
                entityType,
                entityId,
                truncate(detail),
                currentIp()
        ));
    }

    /**
     * The head of the trail, newest first.
     */
    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> recent() {
        return auditRepository
                .findTop200ByOrderByCreatedAtDesc()
                .stream()
                .map(log -> new AdminAuditLogResponse(
                        log.getAuditId(),
                        log.getAction(),
                        log.getEntityType(),
                        log.getEntityId(),
                        log.getDetail(),
                        log.getActor() == null
                                ? null
                                : log.getActor().getFullName(),
                        log.getActor() == null
                                ? null
                                : log.getActor().getEmail(),
                        log.getIpAddress(),
                        log.getCreatedAt()
                ))
                .toList();
    }

    /**
     * Best-effort caller IP from the request context.
     *
     * <p>{@link jakarta.servlet.ServletRequest#getRemoteAddr()}
     * is used rather than a forwarding header: the header is
     * client-controlled unless a trusted proxy rewrites it, and
     * an audit trail should not record an address the caller
     * simply typed. Returns {@code null} on threads without a
     * request (async jobs, tests) -- the action must still be
     * recordable then.
     */
    private static String currentIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes)
                        RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return null;
        }

        HttpServletRequest request = attributes.getRequest();
        return request.getRemoteAddr();
    }

    private static String truncate(String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL) {
            return detail;
        }

        return detail.substring(0, MAX_DETAIL);
    }
}