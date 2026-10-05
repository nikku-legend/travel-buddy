import api from "./api";

/**
 * ============================================================
 * CREATE HOTEL BOOKING
 * ============================================================
 */
async function createHotelBooking(bookingData) {
  const response = await api.post(
    "/bookings/hotel",
    bookingData
  );

  return response.data;
}

/**
 * ============================================================
 * GET SINGLE BOOKING
 * ============================================================
 */
async function getBooking(bookingId) {
  const response = await api.get(
    `/bookings/${bookingId}`
  );

  return response.data;
}

/**
 * ============================================================
 * GET CURRENT USER'S BOOKING HISTORY
 * ============================================================
 */
async function getMyBookings() {
  const response = await api.get(
    "/bookings/my"
  );

  return response.data;
}

/**
 * ============================================================
 * CANCEL BOOKING
 * ============================================================
 *
 * Backend expects:
 *
 * {
 *   reason: "CHANGE_OF_PLANS",
 *   note: "Optional cancellation note"
 * }
 *
 * reason is required.
 * note is optional.
 * ============================================================
 */
async function cancelBooking(
  bookingId,
  cancellationData
) {
  const response = await api.post(
    `/bookings/${bookingId}/cancel`,
    {
      reason: cancellationData?.reason,
      note: cancellationData?.note || null,
    }
  );

  return response.data;
}

/**
 * ============================================================
 * GET CANCELLATION DETAILS
 * ============================================================
 */
async function getCancellation(
  bookingId
) {
  const response = await api.get(
    `/bookings/${bookingId}/cancellation`
  );

  return response.data;
}

/**
 * ============================================================
 * CREATE RAZORPAY PAYMENT ORDER
 * ============================================================
 *
 * Razorpay integration remains disabled until the
 * final payment-integration phase.
 * ============================================================
 */
async function createPaymentOrder(
  bookingId
) {
  const response = await api.post(
    `/bookings/${bookingId}/payment/order`
  );

  return response.data;
}

/**
 * ============================================================
 * VERIFY RAZORPAY PAYMENT
 * ============================================================
 */
async function verifyPayment(
  bookingId,
  paymentData
) {
  const response = await api.post(
    `/bookings/${bookingId}/payment/verify`,
    paymentData
  );

  return response.data;
}

/**
 * ============================================================
 * DEVELOPMENT MOCK PAYMENT
 * ============================================================
 */
async function verifyMockPayment(
  bookingId,
  paymentSuccessful
) {
  const response = await api.post(
    `/bookings/${bookingId}/payment/mock`,
    {
      paymentSuccessful,
    }
  );

  return response.data;
}

/**
 * ============================================================
 * BOOKING SERVICE
 * ============================================================
 */
const bookingService = {
  createHotelBooking,
  getBooking,
  getMyBookings,
  cancelBooking,
  getCancellation,
  createPaymentOrder,
  verifyPayment,
  verifyMockPayment,
};

export default bookingService;