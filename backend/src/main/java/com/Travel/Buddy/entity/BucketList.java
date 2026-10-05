package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "bucket_lists",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_place",
                        columnNames = {
                                "user_id",
                                "place_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_bucket_lists_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_bucket_lists_place",
                        columnList = "place_id"
                )
        }
)
public class BucketList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bucket_id")
    private Long bucketId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "place_id",
            nullable = false
    )
    private TouristPlace place;

    @Column(
            name = "added_at",
            nullable = false
    )
    private LocalDateTime addedAt;

    public BucketList() {
    }

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }

    public Long getBucketId() {
        return bucketId;
    }

    public void setBucketId(Long bucketId) {
        this.bucketId = bucketId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public TouristPlace getPlace() {
        return place;
    }

    public void setPlace(TouristPlace place) {
        this.place = place;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }
}