import api from "./api";

/*
 * ============================================================
 * ADMIN SERVICE  (FR-30 / FR-31 / FR-32 / FR-33)
 * ============================================================
 *
 * Client for the Admin Command Center API. Every path sits
 * under /admin, which the backend restricts to SUPER_ADMIN.
 *
 *   GET    /admin/stats
 *   GET    /admin/audit-log
 *   GET    /admin/users?query=
 *   GET    /admin/users/{id}/history
 *   POST   /admin/users/{id}/suspend
 *   POST   /admin/users/{id}/reactivate
 *   GET    /admin/destinations
 *   POST   /admin/destinations
 *   PUT    /admin/destinations/{id}
 *   GET    /admin/finance/ledger?paymentStatus=&page=&size=
 *   GET    /admin/properties/pending
 *   POST   /admin/properties/{id}/decision
 *   POST   /admin/properties/{id}/suspend
 *
 * Refund decisions live in adminFinanceService, which the
 * refund desk component already owns.
 */
const adminService = {
  /* ---- FR-30: command centre stats + audit trail ---- */

  async getStats() {
    const response = await api.get("/admin/stats");
    return response.data;
  },

  async getAuditLog() {
    const response = await api.get("/admin/audit-log");
    return response.data;
  },

  /* ---- FR-31: user management ---- */

  async getUsers(query) {
    const response = await api.get("/admin/users", {
      params: query ? { query } : {},
    });
    return response.data;
  },

  async getUserHistory(userId) {
    const response = await api.get(`/admin/users/${userId}/history`);
    return response.data;
  },

  async suspendUser(userId, reason) {
    const response = await api.post(`/admin/users/${userId}/suspend`, {
      reason,
    });
    return response.data;
  },

  async reactivateUser(userId) {
    const response = await api.post(
      `/admin/users/${userId}/reactivate`
    );
    return response.data;
  },

  /* ---- FR-32: destination engine ---- */

  async getDestinations() {
    const response = await api.get("/admin/destinations");
    return response.data;
  },

  async createDestination(data) {
    const response = await api.post("/admin/destinations", data);
    return response.data;
  },

  async updateDestination(placeId, data) {
    const response = await api.put(
      `/admin/destinations/${placeId}`,
      data
    );
    return response.data;
  },

  /* ---- FR-33: payment ledger ---- */

  async getLedger({ paymentStatus, page = 0, size = 25 } = {}) {
    const params = { page, size };

    if (paymentStatus) params.paymentStatus = paymentStatus;

    const response = await api.get("/admin/finance/ledger", {
      params,
    });
    return response.data;
  },

  /* ---- Admin approvals: property verification queue ---- */

  async getPendingProperties() {
    const response = await api.get("/admin/properties/pending");
    return response.data;
  },

  async decideProperty(propertyId, approved, reason) {
    const response = await api.post(
      `/admin/properties/${propertyId}/decision`,
      { approved, reason }
    );
    return response.data;
  },

  async suspendProperty(propertyId, reason) {
    const response = await api.post(
      `/admin/properties/${propertyId}/suspend`,
      null,
      { params: { reason } }
    );
    return response.data;
  },
};

export default adminService;
