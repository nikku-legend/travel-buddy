import api from "./api";

const propertyService = {

  async getProperties(stateId = null, propertyType = null) {

    const params = {};

    if (stateId) {
      params.stateId = stateId;
    }

    if (propertyType) {
      params.propertyType = propertyType;
    }

    const response = await api.get("/stays", {
      params,
    });

    return response.data;
  },

  async getPropertyById(propertyId) {

    const response = await api.get(
      `/stays/${propertyId}`
    );

    return response.data;
  },

  async checkAvailability(propertyId, { checkIn, checkOut, guests, rooms }) {

    const response = await api.get(
      `/stays/${propertyId}/availability`,
      {
        params: {
          checkIn,
          checkOut,
          guests,
          rooms,
        },
      }
    );

    return response.data;
  },

};

export default propertyService;
