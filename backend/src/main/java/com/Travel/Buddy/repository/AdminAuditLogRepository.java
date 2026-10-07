package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository
        extends JpaRepository<AdminAuditLog, Long> {

    /**
     * Newest first. The console reads the head of the trail, not
     * the whole thing, and there is no update path that could
     * reorder it.
     */
    List<AdminAuditLog> findTop200ByOrderByCreatedAtDesc();
}