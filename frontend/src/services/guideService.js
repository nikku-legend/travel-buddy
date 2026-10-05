import api from "./api";

/**
 * ============================================================
 * GUIDE SERVICE
 * ============================================================
 */

async function getGuides(stateId) {
  const params = {};
  if (stateId) {
    params.stateId = stateId;
  }
  const response = await api.get("/guides", { params });
  return response.data;
}

async function getGuideById(guideId) {
  const response = await api.get(`/guides/${guideId}`);
  return response.data;
}

async function checkAvailability(guideId, date) {
  const response = await api.get(`/guides/${guideId}/availability`, {
    params: { date },
  });
  return response.data;
}

async function getPartnerProfile() {
  const response = await api.get("/partner/guide/profile");
  return response.data;
}

async function updatePartnerProfile(profileData) {
  const response = await api.post("/partner/guide/profile", profileData);
  return response.data;
}

async function updateAvailability(date, isAvailable) {
  const response = await api.post("/partner/guide/availability", null, {
    params: { date, isAvailable },
  });
  return response.data;
}

const guideService = {
  getGuides,
  getGuideById,
  checkAvailability,
  getPartnerProfile,
  updatePartnerProfile,
  updateAvailability,
};

export default guideService;
