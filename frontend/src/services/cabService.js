import api from "./api";

/**
 * ============================================================
 * CAB & TRANSPORT SERVICE
 * ============================================================
 */

async function searchCabs(params = {}) {
  const response = await api.get("/cabs", { params });
  return response.data;
}

async function getCabById(cabId) {
  const response = await api.get(`/cabs/${cabId}`);
  return response.data;
}

async function bookRide(bookingData) {
  const response = await api.post("/cabs/book", bookingData);
  return response.data;
}

async function getMyRides() {
  const response = await api.get("/cabs/my-rides");
  return response.data;
}

async function getPartnerVehicles() {
  const response = await api.get("/partner/cab/vehicles");
  return response.data;
}

async function addVehicle(vehicleData) {
  const response = await api.post("/partner/cab/vehicles", vehicleData);
  return response.data;
}

async function getPartnerRides() {
  const response = await api.get("/partner/cab/rides");
  return response.data;
}

async function updateRideStatus(rideId, status) {
  const response = await api.patch(`/partner/cab/rides/${rideId}/status`, null, {
    params: { status },
  });
  return response.data;
}

const cabService = {
  searchCabs,
  getCabById,
  bookRide,
  getMyRides,
  getPartnerVehicles,
  addVehicle,
  getPartnerRides,
  updateRideStatus,
};

export default cabService;
