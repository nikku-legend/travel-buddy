import api from "./api";

/**
 * ADD A TOURIST PLACE TO THE CURRENT USER'S BUCKET LIST
 */
async function addToBucketList(placeId) {
  const response = await api.post(
    "/bucket-list",
    {
      placeId,
    }
  );

  return response.data;
}

/**
 * GET CURRENT USER'S BUCKET LIST
 */
async function getMyBucketList() {
  const response = await api.get(
    "/bucket-list"
  );

  return response.data;
}

/**
 * CHECK WHETHER A TOURIST PLACE
 * IS ALREADY SAVED
 */
async function checkBucketList(placeId) {
  const response = await api.get(
    `/bucket-list/check/${placeId}`
  );

  return response.data;
}

/**
 * REMOVE A BUCKET LIST ITEM
 */
async function removeFromBucketList(bucketId) {
  await api.delete(
    `/bucket-list/${bucketId}`
  );

  return true;
}

const bucketListService = {
  addToBucketList,
  getMyBucketList,
  checkBucketList,
  removeFromBucketList,
};

export default bucketListService;