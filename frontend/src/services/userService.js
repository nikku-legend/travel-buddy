import api from "./api";

/**
 * ============================================================
 * USER SERVICE
 * ============================================================
 *
 * Handles authenticated user-related API requests.
 *
 * Backend endpoint:
 *
 * GET /api/v1/user/me
 *
 * Response:
 * {
 *   userId,
 *   fullName,
 *   email,
 *   phoneNumber,
 *   role
 * }
 */

/**
 * Get currently authenticated user's profile.
 */
async function getCurrentUser() {
  const response = await api.get("/user/me");

  return response.data;
}

const userService = {
  getCurrentUser,
};

export default userService;