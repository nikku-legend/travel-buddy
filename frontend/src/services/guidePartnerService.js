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

const guidePartnerService = {
  getMyGuideProfile,
  getMyReservations,
  setAvailability,
};

export default guidePartnerService;