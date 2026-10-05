package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.KycDocument;
import com.Travel.Buddy.entity.KycDocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KycDocumentRepository
        extends JpaRepository<KycDocument, Long> {

    List<KycDocument>
    findByApplication_ApplicationIdOrderByDocumentIdAsc(
            Long applicationId
    );

    Optional<KycDocument>
    findByApplication_ApplicationIdAndDocumentCode(
            Long applicationId,
            String documentCode
    );

    @Query("""
            SELECT document
            FROM KycDocument document
            WHERE document.status IN :statuses
            ORDER BY document.documentId ASC
            """)
    List<KycDocument> findByStatuses(
            @Param("statuses")
            List<KycDocumentStatus> statuses
    );

    long countByStatus(KycDocumentStatus status);

    /**
     * True only when every mandatory document for this application has
     * been uploaded. Used to gate the submit action.
     */
    long countByApplication_ApplicationIdAndStatusNot(
            Long applicationId,
            KycDocumentStatus status
    );
}
