import api from "./api";

/**
 * Home discovery. (SRS 2.3 section 2)
 *
 * The whole page arrives in one response rather than one call
 * per section. Ten separate fetches would mean ten round trips on
 * first paint, and the sections could disagree with each other
 * because each would have been read at a different moment.
 */
async function home() {
  const response = await api.get("/home");
  return response.data;
}

/**
 * Section 2.2 makes "save to bucket list" and "add to current
 * trip" actions on the attraction card itself. Both are
 * authenticated, so the buttons should render disabled for a
 * guest rather than firing a request that would 401.
 *
 * POST /api/v1/bucket-list
 */
async function saveToBucketList(placeId) {
  const response = await api.post("/bucket-list", {
    placeId,
  });
  return response.data;
}

const homeService = {
  home,
  saveToBucketList,
};

export default homeService;