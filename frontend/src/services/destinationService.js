import api from "./api";

const destinationService = {
  async getCountries() {
    const response = await api.get("/geo/countries");

    return response.data;
  },

  async getStates(countryId, region = null) {
    const params = {
      countryId,
    };

    if (region) {
      params.region = region;
    }

    const response = await api.get("/geo/states", {
      params,
    });

    return response.data;
  },

  async getDestinations(stateIds = []) {
    const response = await api.get("/destinations", {
      params: {
        stateIds: stateIds.join(","),
      },
    });

    return response.data;
  },

  async getDestinationById(placeId) {
    const response = await api.get(
      `/destinations/${placeId}`
    );

    return response.data;
  },
};

export default destinationService;