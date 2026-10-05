package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.GuideLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuideLanguageRepository extends JpaRepository<GuideLanguage, Long> {

    List<GuideLanguage> findByGuide_GuideId(Long guideId);
}
