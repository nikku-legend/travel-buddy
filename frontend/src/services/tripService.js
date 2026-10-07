import api from "./api";

/*
 * Custom trip planner. (SRS 2.2, SRS 2.3)
 *
 * Thin wrapper over the planner endpoints. It deliberately holds
 * no state and does no shaping: the backend already returns the
 * route, the cart and the treasure map in one payload, so
 * re-deriving anything here would only create a second answer
 * that can disagree with the first.
 */
const tripService = {

  /* ============================================================
   * TRIPS
   * ============================================================ */

  async getTrips() {
    const response = await api.get("/trip-planner/trips");
    return response.data;
  },

  /**
   * Reads one trip.
   *
   * <p>Deliberately NOT /trip-planner/trips/{id}. The backend keeps
   * the planner surface (build a trip) separate from /my-trips
   * (a journey the traveller already owns), and only the second one
   * exists. Calling the planner path returns 404 and the detail page
   * and the treasure map never load.
   */
  async getTrip(tripId) {
    const response = await api.get(
      `/my-trips/${tripId}`
    );
    return response.data;
  },

  async createTrip(payload) {
    const response = await api.post(
      "/trip-planner/trips",
      payload
    );
    return response.data;
  },

  async saveScope(tripId, zone, regionName, countryId) {
    const response = await api.post(
      `/trip-planner/trips/${tripId}/scope`,
      { zone, regionName, countryId: Number(countryId) }
    );
    return response.data;
  },

  async saveDates(tripId, startDate, endDate) {
    const response = await api.post(
      `/trip-planner/trips/${tripId}/dates`,
      { startDate, endDate }
    );
    return response.data;
  },

  async getCitySuggestions(tripId) {
    const response = await api.get(
      `/trip-planner/trips/${tripId}/city-suggestions`
    );
    return response.data;
  },

  /* ============================================================
   * ROUTE  (TP-02, TP-03)
   *
   * Replaces the whole route. The caller's order is what gets
   * saved, which is why this takes a list and not a delta.
   * ============================================================ */

  async setRoute(tripId, stops) {
    const response = await api.put(
      `/trip-planner/trips/${tripId}/route`,
      { stops }
    );
    return response.data;
  },

  async addPlaces(tripId, places) {
    const response = await api.post(
      `/trip-planner/trips/${tripId}/places`,
      { places }
    );
    return response.data;
  },

  /**
   * The planner's own advice about the CURRENT order.
   *
   * <p>Not a city catalogue. TP-03 is advisory: it proposes an
   * ordering for the stops the traveller already has and warns
   * about it. With no route set there is nothing to advise on,
   * so this returns an empty `stops` list rather than options.
   */
  /**
   * The planner's own advice about the CURRENT order.
   *
   * <p>Not a city catalogue. TP-03 is advisory: it proposes an
   * ordering for the stops the traveller already has and warns
   * about it. With no route set there is nothing to advise on,
   * so this returns an empty stops list rather than options.
   */
  async suggestRoute(tripId) {
    const response = await api.get(
      `/trip-planner/trips/${tripId}/route-suggestion`
    );
    return response.data;
  },

  async getDayAllocation(tripId) {
    const response = await api.get(
      `/trip-planner/trips/${tripId}/day-allocation`
    );
    return response.data;
  },

  /**
   * The cities offered by the city picker.
   *
   * <p>Home discovery is the source because it only surfaces
   * cities that already have content behind them. A free-text
   * picker would happily offer a traveller somewhere with no
   * places, no stays and nothing to do.
   */
  async getCities() {
    const response = await api.get("/home");
    return response.data?.cities ?? [];
  },

  /* ============================================================
   * CART  (TP-06, TP-07)
   * ============================================================ */

  async addSelection(tripId, selection) {
    const response = await api.post(
      `/trip-planner/trips/${tripId}/selections`,
      selection
    );
    return response.data;
  },

  async removeSelection(tripId, selectionId) {
    const response = await api.delete(
      `/trip-planner/trips/${tripId}/selections/${selectionId}`
    );
    return response.data;
  },

  /* ============================================================
   * RECOMMENDATIONS  (TP-08)
   *
   * Recomputed rather than fetched, because availability and
   * prices move under a stored ranking.
   * ============================================================ */

  /**
   * Recomputes and returns the whole trip, not a list.
   *
   * <p>The endpoint answers with the updated TripDetailResponse
   * rather than a bare array, because the ranking, the cart and
   * the map all change together. Treating that as an array made
   * this look like "nothing is available" when the answer was
   * simply wrapped.
   */
  async recomputeRecommendations(tripId) {
    const response = await api.post(
      `/trip-planner/trips/${tripId}/hotel-recommendations`
    );
    return response.data;
  },

  /* ============================================================
   * BILL AND CHECKOUT  (TP-09, TP-10)
   * ============================================================ */

  async getBill(tripId) {
    const response = await api.get(
      `/trip-planner/trips/${tripId}/bill`
    );
    return response.data;
  },

  async previewCheckout(tripId) {
    const response = await api.post(
      `/trip-planner/trips/${tripId}/checkout/preview`
    );
    return response.data;
  },

  /**
   * Agrees to the revalidated total.
   *
   * <p>{@code acceptedTotal} is echoed back from the preview rather
   * than recomputed here. Section 6 requires the traveller to confirm
   * the exact figure they were shown, and the backend rejects a
   * mismatch, so the value must be the one on screen.
   */
  async confirmCheckout(tripId, checkoutId, acceptedTotal, currency) {
    const response = await api.post(
      `/trip-planner/trips/${tripId}/checkout/confirm`,
      { checkoutId, acceptedTotal, currency }
    );
    return response.data;
  },

  async payMockCheckout(tripId, checkoutId, paymentSuccessful) {
    const response = await api.post(
      `/trip-planner/trips/${tripId}/checkout/pay`,
      { checkoutId, paymentSuccessful }
    );
    return response.data;
  },

  async createTripPaymentOrder(tripId, checkoutId) {
    const response = await api.post(
      `/trip-planner/trips/${tripId}/checkout/${checkoutId}/payment/order`
    );
    return response.data;
  },

  async verifyTripPayment(tripId, payment) {
    const response = await api.post(
      `/trip-planner/trips/${tripId}/checkout/payment/verify`,
      payment
    );
    return response.data;
  },

  async getCheckoutHistory(tripId) {
    const response = await api.get(
      `/trip-planner/trips/${tripId}/checkout`
    );
    return response.data;
  },

  /* ============================================================
   * GUIDES AND TRANSPORT  (TP-05)
   *
   * Fetched from the public listings rather than a trip-specific
   * endpoint: there is no guide recommender yet, so offering the
   * real verified guides for a state is more honest than inventing
   * a ranking the backend cannot justify.
   * ============================================================ */

  async getGuidesForState(stateId) {
    const response = await api.get("/guides", {
      params: stateId ? { stateId } : {},
    });
    return Array.isArray(response.data) ? response.data : [];
  },

  async getCabsForState(stateId) {
    const response = await api.get("/cabs", {
      params: stateId ? { stateId } : {},
    });
    return Array.isArray(response.data) ? response.data : [];
  },

  /* ============================================================
   * PREFERENCES
   * ============================================================ */

  async savePreferences(tripId, preferences) {
    const response = await api.post(
      `/trip-planner/trips/${tripId}/preferences`,
      preferences
    );
    return response.data;
  },

  async getWarnings(tripId) {
    const response = await api.get(
      `/trip-planner/trips/${tripId}/warnings`
    );
    return response.data;
  },

  async getTripHealth(tripId) {
    const response = await api.get(
      `/trip-planner/trips/${tripId}/health`
    );
    return response.data;
  },

};

export default tripService;
