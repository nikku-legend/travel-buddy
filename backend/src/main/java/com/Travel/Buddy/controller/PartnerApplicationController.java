package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.partner.*;
import com.Travel.Buddy.entity.PartnerType;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.partner.PartnerApplicationService;
import com.Travel.Buddy.service.partner.KycStorageService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Partner onboarding API.
 *
 * <p>Two audiences share one base path:
 *
 * <ul>
 *   <li>{@code /api/v1/partner/applications/**} - the applicant.</li>
 *   <li>{@code /api/v1/admin/partner-applications/**} - the reviewer.</li>
 * </ul>
 *
 * <p>Keeping them apart means a future mistake in a route annotation
 * cannot accidentally expose the review queue to a normal traveler.
 */
@RestController
@RequestMapping("/api/v1")
public class PartnerApplicationController {

    private final PartnerApplicationService partnerService;

    private final UserRepository userRepository;

    public PartnerApplicationController(
            PartnerApplicationService partnerService,
            UserRepository userRepository
    ) {
        this.partnerService = partnerService;
        this.userRepository = userRepository;
    }

    /* ============================================================
     * PARTNER TYPE CATALOGUE
     *
     * Public so the marketing "Become a Partner" page can render the
     * three options, and their document requirements, before login.
     * ============================================================ */

    @GetMapping("/partner/types")
    public ResponseEntity<List<PartnerTypeOptionResponse>> types() {
        return ResponseEntity.ok(
                partnerService.partnerTypeOptions()
        );
    }

    @GetMapping("/partner/documents/allowed-types")
    public ResponseEntity<Map<String, Object>> allowedUploadTypes() {
        return ResponseEntity.ok(
                Map.of(
                        "allowedContentTypes",
                        KycStorageService.allowedContentTypes(),
                        "maxSizeBytes",
                        10L * 1024L * 1024L
                )
        );
    }

    /* ============================================================
     * APPLICANT
     * ============================================================ */

    @GetMapping("/partner/applications")
    public ResponseEntity<List<PartnerApplicationResponse>>
    myApplications(Authentication authentication) {

        return ResponseEntity.ok(
                partnerService.myApplications(
                        currentUserId(authentication)
                )
        );
    }

    @GetMapping("/partner/applications/{type}")
    public ResponseEntity<PartnerApplicationResponse> getByType(
            Authentication authentication,
            @PathVariable PartnerType type
    ) {

        return ResponseEntity.ok(
                partnerService.getByType(
                        currentUserId(authentication),
                        type
                )
        );
    }

    @PutMapping("/partner/applications/{type}")
    public ResponseEntity<PartnerApplicationResponse> save(
            Authentication authentication,
            @PathVariable PartnerType type,
            @Valid @RequestBody PartnerApplicationRequest request
    ) {

        return ResponseEntity.ok(
                partnerService.upsert(
                        currentUserId(authentication),
                        type,
                        request
                )
        );
    }

    @PostMapping("/partner/applications/{type}/submit")
    public ResponseEntity<PartnerApplicationResponse> submit(
            Authentication authentication,
            @PathVariable PartnerType type
    ) {

        return ResponseEntity.ok(
                partnerService.submit(
                        currentUserId(authentication),
                        type
                )
        );
    }

    @PostMapping("/partner/applications/{type}/withdraw")
    public ResponseEntity<PartnerApplicationResponse> withdraw(
            Authentication authentication,
            @PathVariable PartnerType type
    ) {

        return ResponseEntity.ok(
                partnerService.withdraw(
                        currentUserId(authentication),
                        type
                )
        );
    }

    @PostMapping(
            value = "/partner/applications/{type}/documents/{code}",
            consumes = "multipart/form-data"
    )
    public ResponseEntity<KycDocumentResponse> uploadDocument(
            Authentication authentication,
            @PathVariable PartnerType type,
            @PathVariable String code,
            @RequestParam("file") MultipartFile file
    ) {

        return ResponseEntity.ok(
                partnerService.uploadDocument(
                        currentUserId(authentication),
                        type,
                        code,
                        file
                )
        );
    }

    /* ============================================================
     * ADMIN REVIEW
     * ============================================================ */

    @GetMapping("/admin/partner-applications")
    public ResponseEntity<List<PartnerApplicationResponse>> queue() {
        return ResponseEntity.ok(
                partnerService.reviewQueue()
        );
    }

    @GetMapping("/admin/partner-applications/{applicationId}")
    public ResponseEntity<PartnerApplicationResponse> detail(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(
                partnerService.getForReview(applicationId)
        );
    }

    @PostMapping(
            "/admin/partner-applications/{applicationId}/decision"
    )
    public ResponseEntity<PartnerApplicationResponse> decide(
            Authentication authentication,
            @PathVariable Long applicationId,
            @Valid @RequestBody
            PartnerApplicationDecisionRequest request
    ) {

        return ResponseEntity.ok(
                partnerService.decide(
                        currentUserId(authentication),
                        applicationId,
                        request
                )
        );
    }

    @PutMapping("/admin/kyc-documents/{documentId}/decision")
    public ResponseEntity<KycDocumentResponse> decideDocument(
            Authentication authentication,
            @PathVariable Long documentId,
            @Valid @RequestBody
            KycDocumentDecisionRequest request
    ) {

        return ResponseEntity.ok(
                partnerService.decideDocument(
                        currentUserId(authentication),
                        documentId,
                        request
                )
        );
    }

    /* ============================================================
     * HELPER
     * ============================================================ */

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
