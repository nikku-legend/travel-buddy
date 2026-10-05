import api from "./api";

/*
 * Reviews and the post-trip Review Center. (FR-26, TP-12)
 *
 * Split from tripService because reviews are not part of planning a
 * trip: they outlive it. A Review Center card stays actionable for as
 * long as the booking that earned it exists, which is well past the
 * trip's own end date.
 */

/** Everything reviewable on one trip, with the counts for the header. */
export async function getReviewCentre(tripId) {
  const response = await api.get(`/my-trips/${tripId}/reviews`);
  return response.data;
}

/**
 * How many services on this trip are waiting for a review.
 *
 * Deliberately integer-only and a separate call. The My Trips list
 * badges every trip at once, and fetching the full center for each
 * one would pull every card and every review body just to render a
 * number.
 *
 * Returns 0 rather than throwing: a badge is decoration, and one
 * failing request must not take the trip list down with it.
 */
export async function getPendingCount(tripId) {
  try {
    const response = await api.get(
      `/my-trips/${tripId}/reviews/pending-count`
    );
    return response.data?.pending ?? 0;
  } catch {
    return 0;
  }
}

/**
 * Submits a review.
 *
 * <p>Sends only the target, the rating and the words. No booking id,
 * deliberately: ReviewService picks the booking that proves
 * entitlement, so a caller cannot attach someone else's stay to their
 * own review by naming an id. The client is never the authority on
 * whether this review was earned -- it was rendered because the server
 * said the card was actionable, and the server re-checks that on the
 * write.
 */
export async function submitReview(payload) {
  const response = await api.post("/reviews", payload);
  return response.data;
}

/**
 * Whether this card still takes a review.
 *
 * Mirrors the backend's ReviewUnlockStatus.acceptsReview. A rejected
 * review reopens the card, so "not published yet" is not the same
 * question as "can I write one".
 */
export function canReview(card) {
  return card?.actionable === true;
}

/**
 * What a card is waiting on, in words.
 *
 * The unlock status and the review's own moderation status are two
 * different machines. A card can be SUBMITTED while its review is
 * still PENDING, and a FLAGGED review can sit on an actionable card.
 * Collapsing them would make "write this review" and "this review is
 * live" indistinguishable.
 */
export function statusLabel(card) {
  if (canReview(card)) {
    return card.reviewStatus === "REJECTED" ? "Rewritten" : "To review";
  }

  switch (card.status) {
    case "SUBMITTED":
      return "Awaiting moderation";
    case "PUBLISHED":
      return "Published";
    case "FLAGGED":
      return "Flagged";
    case "ELIGIBLE":
      return "To review";
    case "LOCKED":
      return "Locked";
    default:
      return "Not yet reviewable";
  }
}

/** The service behind a card, named the way the traveller booked it. */
export function targetLabel(targetType) {
  switch (targetType) {
    case "HOTEL":
      return "Stay";
    case "GUIDE":
      return "Guide";
    case "CAB":
      return "Ride";
    case "DESTINATION":
      return "Place";
    default:
      return "Service";
  }
}

/**
 * Server-side field limits, restated for immediate feedback.
 *
 * These duplicate ReviewRequest's annotations. That duplication is
 * worth it: the server is still the authority and rejects anything
 * over the limit, but making someone type 2000 characters to be told
 * it is too long is a poor trade for a few lines.
 */
export const LIMITS = {
  rating: { min: 1, max: 5 },
  title: 150,
  comment: 2000,
};

const reviewService = {
  getReviewCentre,
  getPendingCount,
  submitReview,
  canReview,
  statusLabel,
  targetLabel,
  LIMITS,
};

export default reviewService;