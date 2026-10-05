import api from "./api";

/**
 * In-app notifications. (FR-25)
 *
 * The badge polls unreadCount only, never the full list: the count
 * is a single integer and the list carries every message body, so
 * polling the list would pull the whole inbox on every tick.
 */
async function unreadCount() {
  const response = await api.get("/notifications/unread-count");
  return response.data.unreadCount;
}

/**
 * @param {{page?: number, size?: number, unreadOnly?: boolean}} params
 */
async function list(params = {}) {
  const response = await api.get("/notifications", {
    params: {
      page: params.page ?? 0,
      size: params.size ?? 20,
      unreadOnly: params.unreadOnly ?? false,
    },
  });
  return response.data;
}

async function markRead(notificationId) {
  const response = await api.post(
    `/notifications/${notificationId}/read`
  );
  return response.data;
}

async function markAllRead() {
  const response = await api.post("/notifications/read-all");
  return response.data.updated;
}

const notificationService = {
  unreadCount,
  list,
  markRead,
  markAllRead,
};

export default notificationService;