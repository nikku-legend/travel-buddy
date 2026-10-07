package com.Travel.Buddy.service.partner;

import com.Travel.Buddy.dto.partner.KycDocumentDecisionRequest;
import com.Travel.Buddy.dto.partner.KycDocumentResponse;
import com.Travel.Buddy.dto.partner.KycDocumentTypeResponse;
import com.Travel.Buddy.dto.partner.PartnerApplicationDecisionRequest;
import com.Travel.Buddy.dto.partner.PartnerApplicationRequest;
import com.Travel.Buddy.dto.partner.PartnerApplicationResponse;
import com.Travel.Buddy.dto.partner.PartnerTypeOptionResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.KycDocumentRepository;
import com.Travel.Buddy.repository.KycDocumentTypeRepository;
import com.Travel.Buddy.repository.PartnerApplicationRepository;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.admin.AdminAuditService;
import com.Travel.Buddy.service.notification.NotificationEvents;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Implements the full Travel Buddy partner lifecycle.
 *
 * <pre>
 *   choose type -> fill details -> upload KYC -> submit
 *        -> PENDING_REVIEW -> admin approve -> role granted, dashboard unlocks
 *                        -> admin reject  -> reason shown, applicant corrects
 * </pre>
 *
 * <p>Two rules are enforced throughout and are the reason this lives in
 * a service rather than a controller:
 *
 * <ol>
 *   <li><strong>Applying never grants a role.</strong> The role is written
 *       to {@code user_roles} only inside {@link #decide}, and only when
 *       an admin approves.</li>
 *   <li><strong>An applicant can only ever see their own application.</strong>
 *       Ownership is verified on every read and write.</li>
 * </ol>
 */
@Service
public class PartnerApplicationService {

    private final PartnerApplicationRepository applicationRepository;

    private final KycDocumentRepository documentRepository;

    private final KycDocumentTypeRepository documentTypeRepository;

    private final UserRepository userRepository;

    private final RoleService roleService;

    private final KycStorageService storageService;

    private final NotificationEvents notificationEvents;

    private final AdminAuditService auditService;

    public PartnerApplicationService(
            PartnerApplicationRepository applicationRepository,
            KycDocumentRepository documentRepository,
            KycDocumentTypeRepository documentTypeRepository,
            UserRepository userRepository,
            RoleService roleService,
            KycStorageService storageService,
            NotificationEvents notificationEvents,
            AdminAuditService auditService
    ) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.storageService = storageService;
        this.notificationEvents = notificationEvents;
        this.auditService = auditService;
    }

    /**
     * Maps a partner type to the role it grants once approved.
     */
    public static Role roleFor(PartnerType type) {
        return switch (type) {
            case HOTEL -> Role.ROLE_HOTEL_PARTNER;
            case GUIDE -> Role.ROLE_GUIDE_PARTNER;
            case CAB -> Role.ROLE_CAB_PARTNER;
        };
    }

    /* ============================================================
     * TYPE SELECTION
     * ============================================================ */

    @Transactional(readOnly = true)
    public List<PartnerTypeOptionResponse> partnerTypeOptions() {

        return Arrays.stream(PartnerType.values())
                .map(type -> new PartnerTypeOptionResponse(
                        type,
                        labelFor(type),
                        descriptionFor(type),
                        iconFor(type),
                        requirementsFor(type),
                        requiredDocumentTypes(type)
                ))
                .toList();
    }

    /* ============================================================
     * APPLICANT READS
     * ============================================================ */

    @Transactional
    public PartnerApplicationResponse getByType(
            Long userId,
            PartnerType type
    ) {

        PartnerApplication application =
                requireOwned(userId, type);

        return toResponse(application, true);
    }

    @Transactional(readOnly = true)
    public List<PartnerApplicationResponse> myApplications(
            Long userId
    ) {

        return applicationRepository
                .findByUserId(userId)
                .stream()
                .map(application ->
                        toResponse(application, true)
                )
                .toList();
    }

    /* ============================================================
     * APPLICANT WRITES
     * ============================================================ */

    /**
     * Creates the draft if absent, otherwise updates it. Rejected and
     * pending-correction applications are reopened for editing here
     * rather than creating a duplicate row.
     */
    @Transactional
    public PartnerApplicationResponse upsert(
            Long userId,
            PartnerType type,
            PartnerApplicationRequest request
    ) {

        PartnerApplication application =
                applicationRepository
                        .findByUser_UserIdAndPartnerType(
                                userId,
                                type
                        )
                        .orElseGet(() ->
                                createDraft(userId, type)
                        );

        if (!application.getStatus()
                .isEditableByApplicant()) {

            throw PartnerApplicationException.conflict(
                    "This application cannot be edited while it is "
                            + labelOf(application.getStatus())
            );
        }

        application.setBusinessName(trim(request.businessName()));
        application.setContactPhone(trim(request.contactPhone()));
        application.setAddressLine(trim(request.addressLine()));
        application.setCity(trim(request.city()));
        application.setState(trim(request.state()));
        application.setPostalCode(trim(request.postalCode()));
        application.setTaxIdentifier(trim(request.taxIdentifier()));
        application.setAdditionalInfo(trim(request.additionalInfo()));
        application.setBankAccountName(trim(request.bankAccountName()));
        application.setBankAccountNumber(trim(request.bankAccountNumber()));
        application.setBankIfsc(trim(request.bankIfsc()));

        /*
         * Editing after a rejection moves the application into
         * PENDING_CORRECTION so the admin queue shows it as work in
         * progress rather than a dead REJECTED row.
         */
        if (application.getStatus()
                == PartnerApplicationStatus.REJECTED) {

            application.setStatus(
                    PartnerApplicationStatus.PENDING_CORRECTION
            );
        }

        applicationRepository.save(application);

        return toResponse(application, true);
    }

    /**
     * Moves a draft into the admin review queue.
     *
     * <p>Refuses to submit while mandatory documents are missing, so a
     * reviewer never opens an application that could not be approved.
     */
    @Transactional
    public PartnerApplicationResponse submit(
            Long userId,
            PartnerType type
    ) {

        PartnerApplication application =
                requireOwned(userId, type);

        if (!application.getStatus()
                .isEditableByApplicant()) {

            throw PartnerApplicationException.conflict(
                    "This application has already been submitted"
            );
        }

        if (isBlank(application.getBusinessName())
                || isBlank(application.getContactPhone())
                || isBlank(application.getCity())) {

            throw PartnerApplicationException.badRequest(
                    "Business name, contact phone and city are required before submitting"
            );
        }

        List<String> missing =
                missingMandatoryDocuments(application);

        if (!missing.isEmpty()) {

            throw PartnerApplicationException.badRequest(
                    "Upload the required documents first: "
                            + String.join(", ", missing)
            );
        }

        application.setStatus(
                PartnerApplicationStatus.PENDING_REVIEW
        );
        application.setSubmittedAt(
                LocalDateTime.now()
        );
        application.setRejectionReason(null);
        application.setReviewedAt(null);
        application.setReviewedBy(null);

        applicationRepository.save(application);

        return toResponse(application, true);
    }

    @Transactional
    public PartnerApplicationResponse withdraw(
            Long userId,
            PartnerType type
    ) {

        PartnerApplication application =
                requireOwned(userId, type);

        if (application.getStatus()
                == PartnerApplicationStatus.APPROVED) {

            throw PartnerApplicationException.conflict(
                    "An approved partner account cannot be withdrawn. Contact support to deactivate it."
            );
        }

        application.setStatus(
                PartnerApplicationStatus.WITHDRAWN
        );

        applicationRepository.save(application);

        return toResponse(application, true);
    }


    /* ============================================================
     * KYC DOCUMENTS
     * ============================================================ */

    /**
     * Creates an empty slot for every catalogue document the first time
     * an application is opened, so the UI always has a complete
     * checklist to render rather than only the uploaded entries.
     */
    @Transactional
    public void ensureDocumentSlots(
            PartnerApplication application
    ) {

        for (KycDocumentType type :
                documentTypeRepository
                        .findByPartnerTypeOrderByDisplayOrderAsc(
                                application.getPartnerType()
                        )) {

            boolean exists =
                    documentRepository
                            .findByApplication_ApplicationIdAndDocumentCode(
                                    application.getApplicationId(),
                                    type.getDocumentCode()
                            )
                            .isPresent();

            if (exists) {
                continue;
            }

            KycDocument document = new KycDocument();

            document.setApplication(application);
            document.setDocumentCode(type.getDocumentCode());
            document.setDisplayName(type.getDisplayName());
            document.setStatus(
                    KycDocumentStatus.NOT_UPLOADED
            );

            documentRepository.save(document);
        }
    }

    @Transactional
    public KycDocumentResponse uploadDocument(
            Long userId,
            PartnerType type,
            String documentCode,
            MultipartFile file
    ) {

        PartnerApplication application =
                requireOwned(userId, type);

        if (application.getStatus()
                == PartnerApplicationStatus.APPROVED) {

            throw PartnerApplicationException.conflict(
                    "Documents cannot be changed after approval"
            );
        }

        KycDocument document =
                documentRepository
                        .findByApplication_ApplicationIdAndDocumentCode(
                                application.getApplicationId(),
                                documentCode
                        )
                        .orElseThrow(() ->
                                PartnerApplicationException.notFound(
                                        "Unknown document type: " + documentCode
                                )
                        );

        KycStorageService.StoredFile stored =
                storageService.store(
                        file,
                        application.getApplicationId(),
                        documentCode
                );

        /*
         * Remove the superseded file only after the new one is safely
         * written, so a failed upload never loses the previous document.
         */
        String previousPath = document.getFilePath();

        document.setFileName(stored.fileName());
        document.setFilePath(stored.relativePath());
        document.setContentType(stored.contentType());
        document.setFileSizeBytes(stored.sizeBytes());
        document.setStatus(KycDocumentStatus.UPLOADED);
        document.setUploadedAt(LocalDateTime.now());
        document.setRejectionReason(null);
        document.setVerifiedAt(null);
        document.setVerifiedBy(null);

        documentRepository.save(document);

        if (previousPath != null) {
            storageService.delete(previousPath);
        }

        return toDocumentResponse(document, type);
    }

    /**
     * Verifies or rejects a single document without needing to decide on
     * the whole application, which is what makes a partial and useful
     * review session possible.
     */
    @Transactional
    public KycDocumentResponse decideDocument(
            Long adminId,
            Long documentId,
            KycDocumentDecisionRequest request
    ) {

        KycDocument document =
                documentRepository.findById(documentId)
                        .orElseThrow(() ->
                                PartnerApplicationException.notFound(
                                        "Document not found"
                                )
                        );

        String reason = request.validatedReason();

        document.setStatus(request.status());
        document.setRejectionReason(reason);

        if (request.status() == KycDocumentStatus.VERIFIED) {

            document.setVerifiedAt(LocalDateTime.now());
            document.setVerifiedBy(requireUser(adminId));
        } else {

            document.setVerifiedAt(null);
            document.setVerifiedBy(null);
        }

        documentRepository.save(document);

        if (request.status() == KycDocumentStatus.REJECTED) {
            notificationEvents.kycDocumentRejected(
                    document.getApplication().getUser(),
                    documentId,
                    reason
            );
        }

        auditService.record(
                adminId,
                "KYC_DOCUMENT_DECIDED",
                "KycDocument",
                documentId,
                request.status()
                        + (reason == null || reason.isBlank()
                                ? ""
                                : " - " + reason)
        );

        return toDocumentResponse(
                document,
                document.getApplication().getPartnerType()
        );
    }


    /* ============================================================
     * ADMIN REVIEW
     * ============================================================ */

    @Transactional(readOnly = true)
    public List<PartnerApplicationResponse> reviewQueue() {

        return applicationRepository
                .findByStatuses(
                        List.of(
                                PartnerApplicationStatus.PENDING_REVIEW,
                                PartnerApplicationStatus.PENDING_CORRECTION
                        )
                )
                .stream()
                .map(application ->
                        toResponse(application, false)
                )
                .toList();
    }

    @Transactional
    public PartnerApplicationResponse getForReview(
            Long applicationId
    ) {

        return toResponse(
                requireApplication(applicationId),
                false
        );
    }

    /**
     * Records an admin decision.
     *
     * <p>On approval this is the <em>only</em> place in the entire
     * platform that grants a partner role. The row is locked first so
     * two admins reviewing the same application concurrently cannot
     * both act on it.
     */
    @Transactional
    public PartnerApplicationResponse decide(
            Long adminId,
            Long applicationId,
            PartnerApplicationDecisionRequest request
    ) {

        PartnerApplication application =
                applicationRepository
                        .findByIdForUpdate(applicationId)
                        .orElseThrow(() ->
                                PartnerApplicationException.notFound(
                                        "Partner application not found"
                                )
                        );

        if (!application.getStatus()
                .isAwaitingReview()) {

            throw PartnerApplicationException.conflict(
                    "This application has already been "
                            + labelOf(application.getStatus())
            );
        }

        User admin = requireUser(adminId);

        String reason = request.validatedReason();

        application.setReviewedBy(admin);
        application.setReviewedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(request.approved())) {

            roleService.grant(
                    application.getUser(),
                    roleFor(application.getPartnerType()),
                    admin
            );

            application.setStatus(
                    PartnerApplicationStatus.APPROVED
            );
            application.setRejectionReason(reason);
        } else {

            application.setStatus(
                    PartnerApplicationStatus.REJECTED
            );
            application.setRejectionReason(reason);
        }

        applicationRepository.save(application);

        notificationEvents.partnerApplicationDecided(
                application.getUser(),
                applicationId,
                Boolean.TRUE.equals(request.approved()),
                reason
        );

        auditService.record(
                adminId,
                Boolean.TRUE.equals(request.approved())
                        ? "PARTNER_APPLICATION_APPROVED"
                        : "PARTNER_APPLICATION_REJECTED",
                "PartnerApplication",
                applicationId,
                application.getPartnerType()
                        + (reason == null || reason.isBlank()
                                ? ""
                                : " - " + reason)
        );

        return toResponse(application, false);
    }


    /* ============================================================
     * HELPERS
     * ============================================================ */

    private PartnerApplication createDraft(
            Long userId,
            PartnerType type
    ) {

        PartnerApplication application = new PartnerApplication();

        application.setUser(requireUser(userId));
        application.setPartnerType(type);
        application.setStatus(
                PartnerApplicationStatus.DRAFT
        );

        /*
         * Pre-fill from the existing account so the applicant is never
         * asked to retype information Travel Buddy already holds.
         */
        application.setContactPhone(
                application.getUser().getPhoneNumber()
        );

        PartnerApplication saved =
                applicationRepository.save(application);

        ensureDocumentSlots(saved);

        return saved;
    }

    private PartnerApplication requireOwned(
            Long userId,
            PartnerType type
    ) {

        PartnerApplication application =
                applicationRepository
                        .findByUser_UserIdAndPartnerType(
                                userId,
                                type
                        )
                        .orElseGet(() ->
                                createDraft(userId, type)
                        );

        if (!application.getUser()
                .getUserId()
                .equals(userId)) {

            throw PartnerApplicationException.forbidden(
                    "You cannot access this partner application"
            );
        }

        ensureDocumentSlots(application);

        return application;
    }

    private PartnerApplication requireApplication(
            Long applicationId
    ) {

        PartnerApplication application =
                applicationRepository.findById(applicationId)
                        .orElseThrow(() ->
                                PartnerApplicationException.notFound(
                                        "Partner application not found"
                                )
                        );

        ensureDocumentSlots(application);

        return application;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "User not found"
                        )
                );
    }

    private List<String> missingMandatoryDocuments(
            PartnerApplication application
    ) {

        List<KycDocumentType> required =
                documentTypeRepository
                        .findByPartnerTypeOrderByDisplayOrderAsc(
                                application.getPartnerType()
                        )
                        .stream()
                        .filter(type ->
                                Boolean.TRUE.equals(type.getMandatory())
                        )
                        .toList();

        Map<String, KycDocument> byCode = new HashMap<>();

        for (KycDocument document :
                documentRepository
                        .findByApplication_ApplicationIdOrderByDocumentIdAsc(
                                application.getApplicationId()
                        )) {

            byCode.put(document.getDocumentCode(), document);
        }

        List<String> missing = new ArrayList<>();

        for (KycDocumentType type : required) {

            KycDocument document = byCode.get(type.getDocumentCode());

            if (document == null
                    || document.getStatus()
                    == KycDocumentStatus.NOT_UPLOADED) {

                missing.add(type.getDisplayName());
            }
        }

        return missing;
    }

    private List<KycDocumentTypeResponse> requiredDocumentTypes(
            PartnerType type
    ) {

        return documentTypeRepository
                .findByPartnerTypeOrderByDisplayOrderAsc(type)
                .stream()
                .map(item ->
                        new KycDocumentTypeResponse(
                                item.getDocumentCode(),
                                item.getDisplayName(),
                                Boolean.TRUE.equals(item.getMandatory()),
                                item.getDisplayOrder()
                        )
                )
                .toList();
    }

    private boolean isMandatory(
            PartnerType type,
            String documentCode
    ) {

        return documentTypeRepository
                .findByPartnerTypeOrderByDisplayOrderAsc(type)
                .stream()
                .anyMatch(item ->
                        item.getDocumentCode()
                                .equals(documentCode)
                                && Boolean.TRUE.equals(item.getMandatory())
                );
    }


    /* ============================================================
     * MAPPERS
     * ============================================================ */

    private PartnerApplicationResponse toResponse(
            PartnerApplication application,
            boolean asApplicant
    ) {

        PartnerType type = application.getPartnerType();

        List<KycDocumentResponse> documentResponses =
                new ArrayList<>();

        int mandatoryCount = 0;
        int uploadedCount = 0;

        for (KycDocument document :
                documentRepository
                        .findByApplication_ApplicationIdOrderByDocumentIdAsc(
                                application.getApplicationId()
                        )) {

            boolean mandatory =
                    isMandatory(type, document.getDocumentCode());

            if (mandatory) {
                mandatoryCount++;
            }

            if (document.getStatus()
                    != KycDocumentStatus.NOT_UPLOADED) {
                uploadedCount++;
            }

            documentResponses.add(
                    new KycDocumentResponse(
                            document.getDocumentId(),
                            document.getDocumentCode(),
                            document.getDisplayName(),
                            mandatory,
                            document.getStatus(),
                            document.getFileName(),
                            document.getContentType(),
                            document.getFileSizeBytes(),
                            document.getUploadedAt(),
                            document.getVerifiedAt(),
                            document.getRejectionReason()
                    )
            );
        }

        boolean editable =
                application.getStatus().isEditableByApplicant();

        return new PartnerApplicationResponse(
                application.getApplicationId(),
                type,
                application.getStatus(),
                labelOf(application.getStatus()),
                application.getUser().getUserId(),
                application.getUser().getFullName(),
                application.getUser().getEmail(),
                application.getBusinessName(),
                application.getContactPhone(),
                application.getAddressLine(),
                application.getCity(),
                application.getState(),
                application.getPostalCode(),
                application.getTaxIdentifier(),
                application.getAdditionalInfo(),
                application.getBankAccountName(),
                maskAccountNumber(application.getBankAccountNumber()),
                application.getBankIfsc(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.getReviewedBy() == null
                        ? null
                        : application.getReviewedBy().getFullName(),
                application.getRejectionReason(),
                documentResponses,
                mandatoryCount,
                uploadedCount,
                asApplicant && editable,
                asApplicant
                        && editable
                        && missingMandatoryDocuments(application)
                        .isEmpty(),
                asApplicant
                        && application.getStatus().isAwaitingReview(),
                roleService.hasRole(
                        application.getUser().getUserId(),
                        roleFor(type)
                )
        );
    }

    private KycDocumentResponse toDocumentResponse(
            KycDocument document,
            PartnerType type
    ) {

        return new KycDocumentResponse(
                document.getDocumentId(),
                document.getDocumentCode(),
                document.getDisplayName(),
                isMandatory(type, document.getDocumentCode()),
                document.getStatus(),
                document.getFileName(),
                document.getContentType(),
                document.getFileSizeBytes(),
                document.getUploadedAt(),
                document.getVerifiedAt(),
                document.getRejectionReason()
        );
    }

    /**
     * Never return a full account number. Showing the last four
     * characters is enough to match a statement during verification
     * without exposing the account.
     */
    private String maskAccountNumber(String accountNumber) {

        if (isBlank(accountNumber)) {
            return accountNumber;
        }

        int visible = Math.min(4, accountNumber.length());

        return "XXXX"
                + accountNumber.substring(
                        accountNumber.length() - visible
                );
    }


    /* ============================================================
     * LABELS AND METADATA
     * ============================================================ */

    private String labelFor(PartnerType type) {
        return switch (type) {
            case HOTEL -> "Hotel Partner";
            case GUIDE -> "Guide Partner";
            case CAB -> "Cab / Driver Partner";
        };
    }

    private String descriptionFor(PartnerType type) {
        return switch (type) {
            case HOTEL -> "Hotels, resorts, homestays, guesthouses and lodges";
            case GUIDE -> "Local guides, tour guides and experience providers";
            case CAB -> "Individual drivers, taxi operators and vehicle owners";
        };
    }

    /**
     * Frontend icon key. Kept as plain data so the backend never
     * dictates a UI component library.
     */
    private String iconFor(PartnerType type) {
        return switch (type) {
            case HOTEL -> "building";
            case GUIDE -> "compass";
            case CAB -> "car";
        };
    }

    private String labelOf(PartnerApplicationStatus status) {

        if (status == null) {
            return "unknown";
        }

        return switch (status) {
            case DRAFT -> "a draft";
            case PENDING_REVIEW -> "under review";
            case UNDER_REVIEW -> "under review";
            case APPROVED -> "approved";
            case REJECTED -> "rejected";
            case PENDING_CORRECTION -> "pending correction";
            case WITHDRAWN -> "withdrawn";
        };
    }

    private List<PartnerTypeOptionResponse.PartnerTypeRequirement>
    requirementsFor(PartnerType type) {

        List<PartnerTypeOptionResponse.PartnerTypeRequirement>
                shared = List.of(
                requirement("businessName",
                        "Business or service name", true),
                requirement("contactPhone", "Contact phone", true),
                requirement("addressLine", "Address", false),
                requirement("city", "City", true),
                requirement("state", "State", false),
                requirement("taxIdentifier",
                        "GST / PAN / tax identifier", false),
                requirement("bankAccountName",
                        "Bank account name", false),
                requirement("bankIfsc", "Bank IFSC", false)
        );

        String typeSpecificLabel =
                switch (type) {
                    case HOTEL ->
                            "Property details, room count and amenities";
                    case GUIDE ->
                            "Experience, languages, specialisations "
                                    + "and service areas";
                    case CAB ->
                            "Vehicle type, registration and service areas";
                };

        List<PartnerTypeOptionResponse.PartnerTypeRequirement>
                result = new ArrayList<>(shared);

        result.add(
                requirement("additionalInfo",
                        typeSpecificLabel, false)
        );

        return result;
    }

    private PartnerTypeOptionResponse.PartnerTypeRequirement requirement(
            String field,
            String label,
            boolean required
    ) {

        return new PartnerTypeOptionResponse
                .PartnerTypeRequirement(
                field,
                label,
                required
        );
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

