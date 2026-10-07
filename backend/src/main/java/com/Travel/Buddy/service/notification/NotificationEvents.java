package com.Travel.Buddy.service.notification;

import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.NotificationType;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.Review;
import com.Travel.Buddy.entity.User;
import org.springframework.stereotype.Component;

/**
 * Maps domain events to user-facing notifications. (FR-25)
 *
 * <p>Every piece of notification copy and every dedupe key lives
 * here rather than in the business services. That keeps approval
 * and moderation code readable, and it means the wording of every
 * message the platform sends can be reviewed in one file.
 *
 * <p>Each method names the event it describes, so the call site
 * reads as a statement of what happened:
 *
 * <pre>
 *   notificationEvents.propertyApproved(property);
 * </pre>
 */
@Component
public class NotificationEvents {

    private final NotificationService notificationService;

    public NotificationEvents(
            NotificationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------
    // Partner and property approval
    // ------------------------------------------------------------------

    /**
     * A partner's listing just went live. This is the single most
     * important message a partner receives, so it is emailed.
     */
    public void propertyApproved(Property property) {
        notifyPartner(
                property,
                NotificationType.PROPERTY_APPROVED,
                "Your property is live",
                "\"" + property.getName() + "\" has been approved and is "
                        + "now bookable by travellers.",
                "/partner/properties/" + property.getPropertyId()
        );
    }

    /**
     * Rejection is emailed with the reason attached, because a
     * partner who does not know why cannot fix anything.
     */
    public void propertyRejected(
            Property property,
            String reason
    ) {
        notifyPartner(
                property,
                NotificationType.PROPERTY_REJECTED,
                "Your property was not approved",
                "\"" + property.getName() + "\" was not approved. "
                        + withReason(reason),
                "/partner/properties/" + property.getPropertyId()
        );
    }

    public void propertySuspended(
            Property property,
            String reason
    ) {
        notifyPartner(
                property,
                NotificationType.PROPERTY_SUSPENDED,
                "Your property has been taken offline",
                "\"" + property.getName() + "\" is no longer bookable. "
                        + withReason(reason),
                "/partner/properties/" + property.getPropertyId()
        );
    }

    public void propertySubmitted(Property property) {
        notifyPartner(
                property,
                NotificationType.PROPERTY_SUBMITTED,
                "Property submitted for review",
                "\"" + property.getName() + "\" is with our review team.",
                "/partner/properties/" + property.getPropertyId()
        );
    }

    // ------------------------------------------------------------------
    // Partner onboarding and KYC
    // ------------------------------------------------------------------

    public void partnerApplicationDecided(
            User applicant,
            Long applicationId,
            boolean approved,
            String reason
    ) {
        notificationService.notifyAndEmail(
                applicant.getUserId(),
                approved
                        ? NotificationType.PARTNER_APPLICATION_APPROVED
                        : NotificationType.PARTNER_APPLICATION_REJECTED,
                approved
                        ? "Your partner application is approved"
                        : "Your partner application was not approved",
                approved
                        ? "You can now list your services on Travel Buddy."
                        : "We could not approve your application. "
                                + withReason(reason),
                "PARTNER_APPLICATION",
                applicationId,
                "/partner/applications",
                "partner-app:" + applicationId
                        + ":" + (approved ? "approved" : "rejected")
        );
    }

    public void kycDocumentRejected(
            User partner,
            Long documentId,
            String reason
    ) {
        notificationService.notifyAndEmail(
                partner.getUserId(),
                NotificationType.KYC_DOCUMENT_REJECTED,
                "A verification document was rejected",
                "We could not accept one of your documents. "
                        + withReason(reason),
                "KYC_DOCUMENT",
                documentId,
                "/partner/kyc",
                "kyc-doc:" + documentId + ":rejected"
        );
    }

    // ------------------------------------------------------------------
    // Reviews
    // ------------------------------------------------------------------

    /**
     * Tells the reviewer the outcome of their submission, including
     * the moderator's reason when it was rejected.
     */
    public void reviewModerated(
            Review review,
            User author,
            String targetName,
            boolean approved,
            String reason
    ) {
        notificationService.notify(
                author.getUserId(),
                approved
                        ? NotificationType.REVIEW_PUBLISHED
                        : NotificationType.REVIEW_REJECTED,
                approved
                        ? "Your review is live"
                        : "Your review was not published",
                approved
                        ? "Thanks for sharing your experience of "
                                + targetName + "."
                        : "A moderator did not publish your review of "
                                + targetName + ". " + withReason(reason),
                "REVIEW",
                review.getReviewId(),
                "/bookings",
                "review:" + review.getReviewId() + ":moderated"
        );
    }

    // ------------------------------------------------------------------
    // Bookings
    // ------------------------------------------------------------------

    public void bookingConfirmed(Booking booking) {
        if (booking.getUser() == null) {
            return;
        }
        notificationService.notify(
                booking.getUser().getUserId(),
                NotificationType.BOOKING_CONFIRMED,
                "Your booking is confirmed",
                "Booking " + booking.getBookingReference()
                        + " is confirmed. Your voucher is ready.",
                "BOOKING",
                booking.getBookingId(),
                "/bookings/" + booking.getBookingId(),
                "booking:" + booking.getBookingId() + ":confirmed"
        );
    }

    public void paymentFailed(Booking booking) {
        if (booking.getUser() == null) {
            return;
        }
        notificationService.notifyAndEmail(
                booking.getUser().getUserId(),
                NotificationType.PAYMENT_FAILED,
                "We could not take your payment",
                "Booking " + booking.getBookingReference()
                        + " was not charged. The rooms have been "
                        + "released and you have not been billed.",
                "BOOKING",
                booking.getBookingId(),
                "/bookings/" + booking.getBookingId(),
                "booking:" + booking.getBookingId() + ":payment-failed"
        );
    }

    public void bookingCancelled(
            Booking booking,
            String reason
    ) {
        if (booking.getUser() == null) {
            return;
        }
        notificationService.notify(
                booking.getUser().getUserId(),
                NotificationType.BOOKING_CANCELLED,
                "Your booking was cancelled",
                "Booking " + booking.getBookingReference()
                        + " has been cancelled. "
                        + withReason(reason),
                "BOOKING",
                booking.getBookingId(),
                "/bookings/" + booking.getBookingId(),
                "booking:" + booking.getBookingId() + ":cancelled"
        );
    }

    // ------------------------------------------------------------------
    // Disputes
    // ------------------------------------------------------------------

    /**
     * Tells the claimant their dispute was ruled on, and what was
     * decided. Emailed because a ruling often arrives days after
     * the claim and the claimant is otherwise left waiting.
     */
    public void disputeRuled(
            com.Travel.Buddy.entity.Dispute dispute,
            User claimant,
            String title
    ) {
        String outcome = describe(dispute);

        notificationService.notifyAndEmail(
                claimant.getUserId(),
                NotificationType.DISPUTE_RESOLVED,
                title,
                "\"" + dispute.getSubject() + "\" - " + outcome,
                "DISPUTE",
                dispute.getDisputeId(),
                "/disputes/" + dispute.getDisputeId(),
                "dispute:" + dispute.getDisputeId() + ":ruled"
        );
    }

    /**
     * Plain-language summary of a ruling, used in the notification
     * body so the email agrees with what the dispute page shows.
     */
    private String describe(
            com.Travel.Buddy.entity.Dispute dispute
    ) {
        if (dispute.getResolution() == null) {
            return "The dispute is still being reviewed.";
        }

        java.math.BigDecimal amount = dispute.getResolvedAmount();

        if (amount == null || amount.signum() <= 0) {
            return switch (dispute.getResolution()) {
                case NO_REFUND ->
                        "No refund was awarded."
                                + reason(dispute.getResolutionNotes());
                case CREDIT_NOTE ->
                        "A credit note was issued."
                                + reason(dispute.getResolutionNotes());
                default -> "The dispute was closed.";
            };
        }

        return switch (dispute.getResolution()) {
            case FULL_REFUND -> "A full refund of "
                    + amount + " was approved; it has not yet been "
                    + "confirmed as disbursed."
                    + reason(dispute.getResolutionNotes());
            case PARTIAL_REFUND -> "A partial refund of "
                    + amount + " was approved; it has not yet been "
                    + "confirmed as disbursed."
                    + reason(dispute.getResolutionNotes());
            default -> "The dispute was closed.";
        };
    }

    private String reason(String notes) {
        if (notes == null || notes.isBlank()) {
            return "";
        }
        return " Reason: " + notes;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void notifyPartner(
            Property property,
            NotificationType type,
            String title,
            String body,
            String actionUrl
    ) {
        if (property.getPartner() == null) {
            return;
        }
        Long propertyId = property.getPropertyId();

        notificationService.notifyAndEmail(
                property.getPartner().getUserId(),
                type,
                title,
                body,
                "PROPERTY",
                propertyId,
                actionUrl,
                // Keyed on the property and the outcome, so a
                // re-submission and re-approval notifies again
                // but a retried request does not.
                "property:" + propertyId + ":" + type
        );
    }

    private String withReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        return "Reason: " + reason;
    }
}