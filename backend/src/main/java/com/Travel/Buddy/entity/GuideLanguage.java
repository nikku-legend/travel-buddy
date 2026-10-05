package com.Travel.Buddy.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "guide_languages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_guide_language",
                        columnNames = {"guide_id", "language_name"}
                )
        },
        indexes = {
                @Index(name = "idx_guide_languages_guide", columnList = "guide_id")
        }
)
public class GuideLanguage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_language_id")
    private Long guideLanguageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guide_id", nullable = false)
    private Guide guide;

    @Column(name = "language_name", nullable = false, length = 50)
    private String languageName;

    public GuideLanguage() {}

    public GuideLanguage(Guide guide, String languageName) {
        this.guide = guide;
        this.languageName = languageName;
    }

    public Long getGuideLanguageId() {
        return guideLanguageId;
    }

    public void setGuideLanguageId(Long guideLanguageId) {
        this.guideLanguageId = guideLanguageId;
    }

    public Guide getGuide() {
        return guide;
    }

    public void setGuide(Guide guide) {
        this.guide = guide;
    }

    public String getLanguageName() {
        return languageName;
    }

    public void setLanguageName(String languageName) {
        this.languageName = languageName;
    }
}
