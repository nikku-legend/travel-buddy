import api from "./api";

/*
 * The guide partner portal. (SRS 2.2 FR-16, FR-23)
 *
 * Split from partnerService because that module is the onboarding
 * application workflow, which every partner type shares, while this
 * is specific to guides and only exists after approval.
 */

/** This partner's own guide profile, including the real rating. */
export async function getMyGuideProfile() {
  const response = await api.get("/partner/guide/profile");
  return response.data;
}

/** The tours this guide has actually been given. */
export async function getMyReservations() {
  const response = await api.get("/partner/guide/reservations");
  return Array.isArray(response.data) ? response.data : [];
}

/** Block or open a single day. */
export async function setAvailability(date, isAvailable) {
  const response = await api.post(
    "/partner/guide/availability",
    null,
    { params: { date, isAvailable } }
  );
  return response.data;
}

/**
 * Reports how far a tour has got.
 *
 * Without this a guide has no way to close out a tour, so no guide
 * booking can reach COMPLETED and the traveller never gets a review
 * card for the guide they actually toured with.
 */
export async function updateReservationStatus(reservationId, status) {
  const response = await api.patch(
    `/partner/guide/reservations/${reservationId}/status`,
    null,
    { params: { status } }
  );
  return response.data;
}

/**
 * The status moves a tour is allowed to make next, and the label to
 * show on the button.
 *
 * Mirrors the backend's ALLOWED_TRANSITIONS rather than guessing in
 * the browser: the server refuses an illegal move, so offering one
 * would only produce an error the guide cannot act on.
 */
export function nextStatuses(status) {
  switch (status) {
    case "CONFIRMED":
      return [
        { value: "IN_PROGRESS", label: "Start tour" },
        { value: "CANCELLED", label: "Cancel tour" },
      ];
    case "IN_PROGRESS":
      return [
        { value: "COMPLETED", label: "Mark complete" },
        { value: "CANCELLED", label: "Cancel tour" },
      ];
    default:
      return [];
  }
}

/** A tour in a terminal state is a record and must not be editable. */
export function isFinished(status) {
  return status === "COMPLETED" || status === "CANCELLED";
}

export function statusLabel(status) {
  switch (status) {
    case "CONFIRMED":
      return "Confirmed";
    case "IN_PROGRESS":
      return "In progress";
    case "COMPLETED":
      return "Completed";
    case "CANCELLED":
      return "Cancelled";
    default:
      /* A row written before V54, which the portal still has to render. */
      return "Not reported";
  }
}

const guidePartnerService = {
  getMyGuideProfile,
  getMyReservations,
  setAvailability,
  updateReservationStatus,
  nextStatuses,
  isFinished,
  statusLabel,
};

export default guidePartnerService;