import api from "./api";

/**
 * Disputes. (FR-28)
 *
 * Claimant endpoints and admin endpoints are kept apart so a
 * partner screen can never accidentally call a ruling route.
 */
async function raise(bookingId, payload) {
  const response = await api.post(
    `/bookings/${bookingId}/dispute`,
    payload
  );
  return response.data;
}

async function mine(params = {}) {
  const response = await api.get("/disputes/mine", {
    params: {
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
  });
  return response.data;
}

async function get(disputeId) {
  const response = await api.get(`/disputes/${disputeId}`);
  return response.data;
}

async function withdraw(disputeId) {
  const response = await api.post(
    `/disputes/${disputeId}/withdraw`
  );
  return response.data;
}

async function againstPartner(params = {}) {
  const response = await api.get("/partner/disputes", {
    params: {
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
  });
  return response.data;
}

/* Admin */
async function queue(params = {}) {
  const response = await api.get("/admin/disputes", {
    params: {
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
  });
  return response.data;
}

async function claim(disputeId, note) {
  const response = await api.post(
    `/admin/disputes/${disputeId}/claim`,
    null,
    { params: { note } }
  );
  return response.data;
}

async function changeStatus(disputeId, status, note) {
  const response = await api.post(
    `/admin/disputes/${disputeId}/status`,
    { status, note }
  );
  return response.data;
}

async function resolve(disputeId, payload) {
  const response = await api.post(
    `/admin/disputes/${disputeId}/resolve`,
    payload
  );
  return response.data;
}

const disputeService = {
  raise,
  mine,
  get,
  withdraw,
  againstPartner,
  queue,
  claim,
  changeStatus,
  resolve,
};

export default disputeService;