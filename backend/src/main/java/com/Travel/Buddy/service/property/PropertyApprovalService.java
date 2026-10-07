package com.Travel.Buddy.service.property;

import com.Travel.Buddy.dto.property.PartnerPropertyResponse;
import com.Travel.Buddy.dto.property.PropertyDecisionRequest;
import com.Travel.Buddy.dto.property.PropertyUpsertRequest;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.PropertyStatus;
import com.Travel.Buddy.entity.State;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.PropertyRepository;
import com.Travel.Buddy.repository.RoomTypeRepository;
import com.Travel.Buddy.repository.StateRepository;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.admin.AdminAuditService;
import com.Travel.Buddy.service.notification.NotificationEvents;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Property lifecycle for hotel partners and the admin reviewer. (FR-20)
 *
 * <pre>
 *   create -> DRAFT -> submit -> PENDING_APPROVAL
 *                                    |
 *                       +------------+------------+
 *                       |                         |
 *                  approve                     reject
 *                       |                         |
 *                       v                         v
 *                   APPROVED                  REJECTED
 *                    (live)                       |
 *                                             edit and resubmit
 *
 *   APPROVED -- suspend --> SUSPENDED
 * </pre>
 *
 * <p>Two invariants this class exists to enforce:
 *
 * <ol>
 *   <li><strong>Partners cannot publish.</strong> No code path
 *       reachable by a partner sets APPROVED. Only {@code decide} can,
 *       and only on behalf of an authenticated admin.</li>
 *   <li><strong>status and is_verified never diverge.</strong> The
 *       public read queries filter on is_verified, so both are always
 *       written together in {@code applyStatus}.</li>
 * </ol>
 */
@Service
public class PropertyApprovalService {

    private final PropertyRepository propertyRepository;

    private final RoomTypeRepository roomTypeRepository;

    private final StateRepository stateRepository;

    private final UserRepository userRepository;

    private final NotificationEvents notificationEvents;

    private final AdminAuditService auditService;

    public PropertyApprovalService(
            PropertyRepository propertyRepository,
            RoomTypeRepository roomTypeRepository,
            StateRepository stateRepository,
            UserRepository userRepository,
            NotificationEvents notificationEvents,
            AdminAuditService auditService
    ) {
        this.propertyRepository = propertyRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.stateRepository = stateRepository;
        this.userRepository = userRepository;
        this.notificationEvents = notificationEvents;
        this.auditService = auditService;
    }

    /* ============================================================
     * PARTNER SIDE
     * ============================================================ */

    @Transactional(readOnly = true)
    public List<PartnerPropertyResponse> myProperties(
            Long partnerId
    ) {
        return propertyRepository
                .findByPartner_UserIdOrderByNameAsc(partnerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PartnerPropertyResponse create(
            User partner,
            PropertyUpsertRequest request
    ) {
        State state = requireState(request.stateId());

        Property property = new Property();

        property.setPartner(partner);
        property.setName(request.name().trim());
        property.setPropertyType(request.propertyType());
        property.setState(state);
        property.setAddress(request.address().trim());
        property.setDescription(trim(request.description()));
        property.setLatitude(request.latitude());
        property.setLongitude(request.longitude());

        /*
         * A new property is always a private draft. This single line is
         * what stops a partner publishing without review.
         */
        applyStatus(property, PropertyStatus.DRAFT);

        return toResponse(propertyRepository.save(property));
    }

    @Transactional
    public PartnerPropertyResponse update(
            Long partnerId,
            Long propertyId,
            PropertyUpsertRequest request
    ) {
        Property property = requireOwned(partnerId, propertyId);

        if (!property.getStatus().isEditableByPartner()) {

            throw PartnerApplicationException.conflict(
                    "This property cannot be edited while it is "
                            + property.getStatus()
            );
        }

        State state = requireState(request.stateId());

        property.setName(request.name().trim());
        property.setPropertyType(request.propertyType());
        property.setState(state);
        property.setAddress(request.address().trim());
        property.setDescription(trim(request.description()));
        property.setLatitude(request.latitude());
        property.setLongitude(request.longitude());

        /*
         * Correcting a rejected property returns it to DRAFT so the
         * partner can finish before resubmitting, rather than going
         * straight back into the review queue.
         */
        if (property.getStatus() == PropertyStatus.REJECTED) {

            property.setStatus(PropertyStatus.DRAFT);
            property.setRejectionReason(null);
        }

        return toResponse(propertyRepository.save(property));
    }

    /**
     * Sends a property for review.
     *
     * <p>Refuses to submit a property with no rooms, because an
     * approved listing with nothing bookable is worse than no listing.
     */
    @Transactional
    public PartnerPropertyResponse submit(
            Long partnerId,
            Long propertyId
    ) {
        Property property = requireOwned(partnerId, propertyId);

        if (!property.getStatus().isEditableByPartner()) {

            throw PartnerApplicationException.conflict(
                    "This property has already been submitted"
            );
        }

        if (roomTypeRepository
                .countByProperty_PropertyId(propertyId) == 0) {

            throw PartnerApplicationException.badRequest(
                    "Add at least one room type before submitting for approval"
            );
        }

        property.setStatus(PropertyStatus.PENDING_APPROVAL);
        property.setSubmittedAt(LocalDateTime.now());
        property.setRejectionReason(null);
        property.setReviewedAt(null);
        property.setReviewedBy(null);

        PartnerPropertyResponse response =
                toResponse(propertyRepository.save(property));

        notificationEvents.propertySubmitted(property);

        return response;
    }

    /* ============================================================
     * ADMIN SIDE
     * ============================================================ */

    @Transactional(readOnly = true)
    public List<PartnerPropertyResponse> approvalQueue() {

        return propertyRepository
                .findByStatusOrderBySubmittedAtAsc(
                        PropertyStatus.PENDING_APPROVAL
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PartnerPropertyResponse getForReview(
            Long propertyId
    ) {
        return toResponse(requireProperty(propertyId));
    }

    /**
     * Approve or reject a submitted property.
     *
     * <p>The row is locked so two admins cannot both act on the same
     * listing, and this is the only path that makes a property publicly
     * visible.
     */
    @Transactional
    public PartnerPropertyResponse decide(
            Long adminId,
            Long propertyId,
            PropertyDecisionRequest request
    ) {
        Property property =
                propertyRepository.findByIdForUpdate(propertyId)
                        .orElseThrow(() ->
                                PartnerApplicationException.notFound(
                                        "Property not found"
                                )
                        );

        if (property.getStatus()
                != PropertyStatus.PENDING_APPROVAL) {

            throw PartnerApplicationException.conflict(
                    "This property is not awaiting review"
            );
        }

        User admin = requireUser(adminId);

        String reason = request.validatedReason();

        property.setReviewedBy(admin);
        property.setReviewedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(request.approved())) {

            applyStatus(property, PropertyStatus.APPROVED);
        } else {

            applyStatus(property, PropertyStatus.REJECTED);
        }

        property.setRejectionReason(reason);

        PartnerPropertyResponse response =
                toResponse(propertyRepository.save(property));

        if (Boolean.TRUE.equals(request.approved())) {
            notificationEvents.propertyApproved(property);
        } else {
            notificationEvents.propertyRejected(property, reason);
        }

        auditService.record(
                adminId,
                Boolean.TRUE.equals(request.approved())
                        ? "PROPERTY_APPROVED"
                        : "PROPERTY_REJECTED",
                "Property",
                propertyId,
                property.getName()
                        + (reason == null || reason.isBlank()
                                ? ""
                                : " - " + reason)
        );

        return response;
    }

    /**
     * Pulls a live property off the marketplace without deleting it.
     */
    @Transactional
    public PartnerPropertyResponse suspend(
            Long adminId,
            Long propertyId,
            String reason
    ) {
        Property property =
                propertyRepository.findByIdForUpdate(propertyId)
                        .orElseThrow(() ->
                                PartnerApplicationException.notFound(
                                        "Property not found"
                                )
                        );

        if (!property.getStatus().isLive()) {

            throw PartnerApplicationException.conflict(
                    "Only a live property can be suspended"
            );
        }

        if (reason == null || reason.isBlank()) {

            throw new IllegalArgumentException(
                    "A reason is required to suspend a property"
            );
        }

        property.setReviewedBy(requireUser(adminId));
        property.setReviewedAt(LocalDateTime.now());
        property.setRejectionReason(reason.trim());

        applyStatus(property, PropertyStatus.SUSPENDED);

        PartnerPropertyResponse response =
                toResponse(propertyRepository.save(property));

        notificationEvents.propertySuspended(
                property, reason.trim()
        );

        auditService.record(
                adminId,
                "PROPERTY_SUSPENDED",
                "Property",
                propertyId,
                property.getName() + " - " + reason.trim()
        );

        return response;
    }

    /* ============================================================
     * HELPERS
     * ============================================================ */

    /**
     * The single place {@code is_verified} is ever written.
     *
     * <p>Public listing queries and availability checks both filter on
     * {@code is_verified}, so keeping it derived from {@code status} in
     * one method is what prevents a property from being visible without
     * having been approved.
     */
    private void applyStatus(
            Property property,
            PropertyStatus status
    ) {
        property.setStatus(status);
        property.setVerified(status.isLive());
    }

    private Property requireOwned(
            Long partnerId,
            Long propertyId
    ) {
        Property property = requireProperty(propertyId);

        if (!property.getPartner()
                .getUserId()
                .equals(partnerId)) {

            throw PartnerApplicationException.forbidden(
                    "You do not own this property"
            );
        }

        return property;
    }

    private Property requireProperty(Long propertyId) {
        return propertyRepository.findById(propertyId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Property not found"
                        )
                );
    }

    private State requireState(Integer stateId) {
        return stateRepository.findById(stateId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "State not found: " + stateId
                        )
                );
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Admin not found"
                        )
                );
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /* ============================================================
     * MAPPER
     * ============================================================ */

    private PartnerPropertyResponse toResponse(
            Property property
    ) {
        boolean editable =
                property.getStatus().isEditableByPartner();

        return new PartnerPropertyResponse(
                property.getPropertyId(),
                property.getName(),
                property.getPropertyType(),
                property.getStatus(),
                labelOf(property.getStatus()),
                property.getState() == null
                        ? null
                        : property.getState().getStateId(),
                property.getState() == null
                        ? null
                        : property.getState().getName(),
                property.getAddress(),
                property.getDescription(),
                property.getLatitude(),
                property.getLongitude(),
                property.getStatus().isLive(),
                property.getRejectionReason(),
                property.getSubmittedAt(),
                property.getReviewedAt(),
                property.getReviewedBy() == null
                        ? null
                        : property.getReviewedBy().getFullName(),
                editable,
                editable,
                roomTypeRepository.countByProperty_PropertyId(
                        property.getPropertyId()
                )
        );
    }

    private String labelOf(PropertyStatus status) {

        if (status == null) {
            return "unknown";
        }

        return switch (status) {
            case DRAFT -> "draft";
            case PENDING_APPROVAL -> "pending approval";
            case APPROVED -> "live";
            case REJECTED -> "rejected";
            case SUSPENDED -> "suspended";
        };
    }
}
