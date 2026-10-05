import api from "./api";

/*
 * ============================================================
 * CAB PARTNER PORTAL
 *
 *   Vehicles   GET  /partner/cab/vehicles
 *   Rides      GET  /partner/cab/rides
 *              PATCH /partner/cab/rides/{rideId}/status?status=
 *
 * Verification is read from each vehicle's own isVerified flag.
 * It is deliberately not hard-coded to "Verified" on the card:
 * an unverified cab shown as verified is a promise the platform
 * has not checked.
 */

const asList = (data) => (Array.isArray(data) ? data : []);

/**
 * The status moves the backend accepts, mirrored from
 * CabService.ALLOWED_TRANSITIONS.
 *
 * <p>Kept here so the portal can show exactly one button per legal next
 * step instead of a dropdown of every status. The server re-checks this
 * either way — it is a convenience, never the authority.
 *
 * <p>COMPLETED and CANCELLED are absent as keys because they are
 * terminal. A finished ride is a record of what happened, and letting a
 * partner edit it makes the ledger disagree with the audit trail.
 */
const NEXT_STATUSES = {
  CONFIRMED: ["DRIVER_ASSIGNED", "CANCELLED"],
  DRIVER_ASSIGNED: ["IN_PROGRESS", "CANCELLED"],
  IN_PROGRESS: ["COMPLETED"],
};

/** What a driver can usefully do from where this ride currently sits. */
export function nextStatuses(status) {
  return NEXT_STATUSES[status] ?? [];
}

export function isFinished(status) {
  return status === "COMPLETED" || status === "CANCELLED";
}

/** Labels for the status buttons, so the wording is decided once. */
const STATUS_LABELS = {
  DRIVER_ASSIGNED: "Start pickup",
  IN_PROGRESS: "Passenger on board",
  COMPLETED: "Complete ride",
  CANCELLED: "Cancel ride",
};

export function statusLabel(status) {
  return STATUS_LABELS[status] ?? String(status).replace(/_/g, " ");
}

export async function getMyVehicles() {
  const response = await api.get("/partner/cab/vehicles");
  return asList(response.data);
}

export async function getMyRides() {
  const response = await api.get("/partner/cab/rides");
  return asList(response.data);
}

/**
 * Move a ride along its lifecycle.
 *
 * <p>The status is sent as a query parameter, not a body, because
 * that is what the endpoint declares. Guessing here would produce a
 * 400 that looks like a server fault.
 */
export async function updateRideStatus(rideId, status) {
  const response = await api.patch(
    `/partner/cab/rides/${rideId}/status`,
    null,
    { params: { status } }
  );
  return response.data;
}

const cabPartnerService = {
  getMyVehicles,
  getMyRides,
  updateRideStatus,
  nextStatuses,
  isFinished,
  statusLabel,
};

export default cabPartnerService;
