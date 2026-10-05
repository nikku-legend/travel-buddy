import axios from "axios";

const api = axios.create({
  /*
   * The API origin comes from VITE_API_BASE_URL so the same bundle can
   * point at localhost during development and at the deployed backend in
   * production, without a rebuild. Vite only exposes variables prefixed
   * with VITE_ to client code.
   */
  baseURL:
    import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api/v1",
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("accessToken");

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

/*
 * Access tokens intentionally expire quickly. When that happens, use the
 * refresh token once and repeat the original request transparently. This
 * keeps an existing signed-in session working after the 15-minute access
 * token lifetime without retrying endlessly on a failed refresh.
 */
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const status = error.response?.status;
    const refreshToken = localStorage.getItem("refreshToken");

    if (
      (status === 401 || status === 403) &&
      refreshToken &&
      !originalRequest?._retry &&
      !originalRequest?.url?.includes("/auth/refresh")
    ) {
      originalRequest._retry = true;

      try {
        const response = await api.post(
          "/auth/refresh",
          { refreshToken },
          { _skipAuthRefresh: true }
        );

        localStorage.setItem("accessToken", response.data.accessToken);
        localStorage.setItem("refreshToken", response.data.refreshToken);
        originalRequest.headers.Authorization =
          `Bearer ${response.data.accessToken}`;

        return api(originalRequest);
      } catch (refreshError) {
        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default api;
