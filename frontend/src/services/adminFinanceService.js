import api from "./api";

const adminFinanceService = {
  async getRefundQueue() {
    const response = await api.get("/admin/finance/refunds");
    return response.data;
  },

  async decideRefund(cancellationId, approve, reason) {
    const response = await api.post(
      `/admin/finance/refunds/${cancellationId}`,
      { approve, reason }
    );
    return response.data;
  },
};

export default adminFinanceService;
