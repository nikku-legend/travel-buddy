package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.review.ReviewDecisionRequest;
import com.Travel.Buddy.dto.review.ReviewRequest;
import com.Travel.Buddy.dto.review.ReviewResponse;
import com.Travel.Buddy.dto.review.ReviewSummaryResponse;
import com.Travel.Buddy.entity.ReviewTargetType;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.review.ReviewService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Reviews and ratings. (FR-26)
 *
 * <p>Public reads are separated from writes here so a published
 * review list can never be served from a query that forgets to
 * filter on status.
 */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviewService;

    private final UserRepository userRepository;

    public ReviewController(
            ReviewService reviewService,
            UserRepository userRepository
    ) {
        this.reviewService = reviewService;
        this.userRepository = userRepository;
    }

    /* ============================================================
     * PUBLIC
     * ============================================================ */

    /**
     * Published reviews for a hotel, guide, cab or destination.
     */
    @GetMapping("/reviews/{targetType}/{targetId}")
    public ResponseEntity<List<ReviewResponse>> publicReviews(
            @PathVariable ReviewTargetType targetType,
            @PathVariable Long targetId
    ) {
        return ResponseEntity.ok(
                reviewService.publicReviews(targetType, targetId)
        );
    }

    @GetMapping("/reviews/{targetType}/{targetId}/summary")
    public ResponseEntity<ReviewSummaryResponse> summary(
            @PathVariable ReviewTargetType targetType,
            @PathVariable Long targetId
    ) {
        return ResponseEntity.ok(
                reviewService.summary(targetType, targetId)
        );
    }

    /* ============================================================
     * TRAVELLER
     * ============================================================ */

    /**
     * Submits a review. Requires a completed stay, enforced
     * server-side.
     */
    @PostMapping("/reviews")
    public ResponseEntity<ReviewResponse> submit(
            Authentication authentication,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        reviewService.submit(
                                currentUserId(authentication),
                                request
                        )
                );
    }

    @GetMapping("/reviews/mine")
    public ResponseEntity<List<ReviewResponse>> myReviews(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                reviewService.myReviews(
                        currentUserId(authentication)
                )
        );
    }

    /**
     * Whether the signed-in user may review a subject, so the UI can
     * show the prompt only when it will actually succeed.
     */
    @GetMapping("/reviews/eligibility")
    public ResponseEntity<Map<String, Boolean>> eligibility(
            Authentication authentication,
            @RequestParam ReviewTargetType targetType,
            @RequestParam Long targetId
    ) {
        return ResponseEntity.ok(
                Map.of(
                        "eligible",
                        reviewService.isEligible(
                                currentUserId(authentication),
                                targetType,
                                targetId
                        )
                )
        );
    }

    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<ReviewResponse> edit(
            Authentication authentication,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ResponseEntity.ok(
                reviewService.edit(
                        currentUserId(authentication),
                        reviewId,
                        request
                )
        );
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> withdraw(
            Authentication authentication,
            @PathVariable Long reviewId
    ) {
        reviewService.withdraw(
                currentUserId(authentication),
                reviewId
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * Reports a review for moderator attention.
     */
    @PostMapping("/reviews/{reviewId}/report")
    public ResponseEntity<ReviewResponse> report(
            Authentication authentication,
            @PathVariable Long reviewId
    ) {
        return ResponseEntity.ok(
                reviewService.flag(
                        currentUserId(authentication),
                        reviewId
                )
        );
    }

    /* ============================================================
     * MODERATION (FR-29)
     * ============================================================ */

    @GetMapping("/admin/reviews/pending")
    public ResponseEntity<List<ReviewResponse>> queue() {
        return ResponseEntity.ok(
                reviewService.moderationQueue()
        );
    }

    @GetMapping("/admin/reviews/flagged")
    public ResponseEntity<List<ReviewResponse>> flaggedQueue() {
        return ResponseEntity.ok(reviewService.flaggedQueue());
    }

    @PostMapping("/admin/reviews/{reviewId}/decision")
    public ResponseEntity<ReviewResponse> decide(
            Authentication authentication,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewDecisionRequest request
    ) {
        return ResponseEntity.ok(
                reviewService.moderate(
                        currentUserId(authentication),
                        reviewId,
                        request
                )
        );
    }

    private Long currentUserId(Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new AccessDeniedException(
                    "Authentication is required"
            );
        }

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Authenticated user no longer exists"
                        )
                );

        return user.getUserId();
    }
}