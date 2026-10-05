package com.Travel.Buddy.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for {@link ReviewSummary}.
 *
 * <p>A subject is identified by its type plus its id, because the
 * same numeric id can be a property, a guide, a cab or a place.
 */
public class ReviewSummaryId
        implements Serializable {

    private ReviewTargetType targetType;

    private Long targetId;

    public ReviewSummaryId() {
    }

    public ReviewSummaryId(
            ReviewTargetType targetType,
            Long targetId
    ) {
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public ReviewTargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(ReviewTargetType targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof ReviewSummaryId that)) {
            return false;
        }

        return targetType == that.targetType
                && Objects.equals(targetId, that.targetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetType, targetId);
    }
}