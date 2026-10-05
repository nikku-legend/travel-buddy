package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.KycDocumentType;
import com.Travel.Buddy.entity.PartnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KycDocumentTypeRepository
        extends JpaRepository<KycDocumentType, Long> {

    List<KycDocumentType>
    findByPartnerTypeOrderByDisplayOrderAsc(
            PartnerType partnerType
    );
}
