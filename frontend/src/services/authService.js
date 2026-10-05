import api from "./api";

const authService = {
  async register(data) {
    const response = await api.post("/auth/register", data);
    return response.data;
  },

  async login(data) {
    const response = await api.post("/auth/login", data);
    return response.data;
  },

  async refresh(refreshToken) {
    const response = await api.post("/auth/refresh", {
      refreshToken,
    });

    return response.data;
  },

  async logout(refreshToken) {
    const response = await api.post("/auth/logout", {
      refreshToken,
    });

    return response.data;
  },

  async getCurrentUser() {
    const response = await api.get("/user/me");
    return response.data;
  },
};

export default authService;