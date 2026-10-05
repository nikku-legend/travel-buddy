import api from "./api";

/*
 * ============================================================
 * HOTEL PARTNER PORTAL
 *
 * Properties, rooms, inventory, the front desk and content
 * management. Split from partnerService, which is the shared
 * onboarding application every partner type uses, because
 * none of this exists until the partner has been approved.
 *
 *   Properties   GET  /partner/hotel/properties
 *   Rooms        GET  /partner/hotel/properties/{id}/rooms
 *   Inventory    GET  /partner/hotel/properties/{id}/rooms/{rid}/calendar
 *   Rooms (real) GET  /partner/hotel/properties/{id}/physical-rooms
 *   Front desk   GET  /partner/hotel/reservations
 *                GET  /partner/hotel/front-desk
 *
 * Nothing here decides anything. Every list is normalised to an
 * array so a caller never has to guard, but permission and
 * availability are always the backend's answer.
 */

const asList = (data) => (Array.isArray(data) ? data : []);

/** Properties this partner owns, with their approval state. */
export async function getMyProperties() {
  const response = await api.get("/partner/hotel/properties");
  return asList(response.data);
}

/** Room types for a property, including their inventory coverage. */
export async function getRoomTypes(propertyId) {
  const response = await api.get(
    `/partner/hotel/properties/${propertyId}/rooms`
  );
  return asList(response.data);
}

/**
 * The partner's arrivals.
 *
 * <p>Prefers /reservations over /front-desk because front-desk only
 * returns guests who already have a room number, which hides the
 * booking that most needs attention.
 */
export async function getReservations() {
  const response = await api.get("/partner/hotel/reservations");
  return asList(response.data);
}

/** Guests who already hold a room and are checked in. */
export async function getFrontDesk() {
  const response = await api.get("/partner/hotel/front-desk");
  return asList(response.data);
}

/** Real numbered rooms, flagged occupied in the given window. */
export async function getPhysicalRooms(propertyId, { from, to } = {}) {
  const response = await api.get(
    `/partner/hotel/properties/${propertyId}/physical-rooms`,
    { params: { from, to } }
  );
  return asList(response.data);
}

/** Nightly inventory for one room type. */
export async function getInventoryCalendar(
  propertyId,
  roomTypeId,
  from,
  to
) {
  const response = await api.get(
    `/partner/hotel/properties/${propertyId}/rooms/${roomTypeId}/calendar`,
    { params: { from, to } }
  );
  return asList(response.data);
}

/** Give a booking a specific numbered room. */
export async function assignRoom(bookingId, physicalRoomId) {
  const response = await api.post(
    `/partner/hotel/bookings/${bookingId}/assignments`,
    { physicalRoomId }
  );
  return response.data;
}

/** Release a room back to the pool before the guest arrives. */
export async function unassignStay(stayId) {
  const response = await api.delete(
    `/partner/hotel/assignments/${stayId}`
  );
  return response.data;
}

export async function checkIn(stayId) {
  const response = await api.post(
    `/partner/hotel/assignments/${stayId}/check-in`
  );
  return response.data;
}

export async function checkOut(stayId) {
  const response = await api.post(
    `/partner/hotel/assignments/${stayId}/check-out`
  );
  return response.data;
}

/** The guest never arrived and did not cancel. */
export async function markNoShow(stayId) {
  const response = await api.post(
    `/partner/hotel/assignments/${stayId}/no-show`
  );
  return response.data;
}

const hotelPartnerService = {
  getMyProperties,
  getRoomTypes,
  getReservations,
  getFrontDesk,
  getPhysicalRooms,
  getInventoryCalendar,
  assignRoom,
  unassignStay,
  checkIn,
  checkOut,
  markNoShow,
};

export default hotelPartnerService;
