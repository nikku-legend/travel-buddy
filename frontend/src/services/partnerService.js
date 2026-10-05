import api from "./api";

/*
 * ============================================================
 * PARTNER SERVICE
 * ============================================================
 *
 * Becoming a Travel Buddy partner never creates a second account.
 * An existing traveler submits an application, an admin reviews it,
 * and the partner role is only granted on approval.
 *
 * Public catalogue:
 *   GET  /api/v1/partner/types
 *
 * Applicant (any authenticated user):
 *   GET  /api/v1/partner/applications
 *   GET  /api/v1/partner/applications/{type}
 *   PUT  /api/v1/partner/applications/{type}
 *   POST /api/v1/partner/applications/{type}/submit
 *   POST /api/v1/partner/applications/{type}/withdraw
 *   POST /api/v1/partner/applications/{type}/documents/{code}
 *
 * Admin review:
 *   GET  /api/v1/admin/partner-applications
 *   GET  /api/v1/admin/partner-applications/{id}
 *   POST /api/v1/admin/partner-applications/{id}/decision
 *   PUT  /api/v1/admin/kyc-documents/{id}/decision
 */

/** Partner types and their requirements. Public. */
export async function getPartnerTypes() {
  const response = await api.get("/partner/types");

  return response.data;
}

/** Upload constraints, so the UI never hard-codes them. */
export async function getAllowedUploadTypes() {
  const response = await api.get("/partner/documents/allowed-types");

  return response.data;
}

/** Every application belonging to the signed-in user. */
export async function getMyApplications() {
  const response = await api.get("/partner/applications");

  return response.data;
}

/** One application by partner type, creating a draft if needed. */
export async function getApplication(partnerType) {
  const response = await api.get(
    `/partner/applications/${partnerType}`
  );

  return response.data;
}

/** Create or update the application details. */
export async function saveApplication(partnerType, payload) {
  const response = await api.put(
    `/partner/applications/${partnerType}`,
    payload
  );

  return response.data;
}

/** Move the application into the admin review queue. */
export async function submitApplication(partnerType) {
  const response = await api.post(
    `/partner/applications/${partnerType}/submit`
  );

  return response.data;
}

/** Cancel an application the applicant no longer wants. */
export async function withdrawApplication(partnerType) {
  const response = await api.post(
    `/partner/applications/${partnerType}/withdraw`
  );

  return response.data;
}

/**
 * Upload one KYC document.
 *
 * FormData is sent without an explicit Content-Type so the browser sets
 * the multipart boundary itself; overriding it breaks the upload.
 */
export async function uploadDocument(partnerType, documentCode, file) {
  const formData = new FormData();

  formData.append("file", file);

  const response = await api.post(
    `/partner/applications/${partnerType}/documents/${documentCode}`,
    formData,
    {
      headers: { "Content-Type": "multipart/form-data" },
    }
  );

  return response.data;
}

/* ============================================================
 * ADMIN
 * ============================================================ */

/** Applications awaiting a decision. */
export async function getReviewQueue() {
  const response = await api.get("/admin/partner-applications");

  return response.data;
}

/** Full application detail for the review screen. */
export async function getApplicationForReview(applicationId) {
  const response = await api.get(
    `/admin/partner-applications/${applicationId}`
  );

  return response.data;
}

/**
 * Approve or reject an application.
 *
 * A reason is mandatory when rejecting; the backend rejects the request
 * without it so the applicant is always told what to fix.
 */
export async function decideApplication(
  applicationId,
  approved,
  reason
) {
  const response = await api.post(
    `/admin/partner-applications/${applicationId}/decision`,
    { approved, reason }
  );

  return response.data;
}

/** Verify or reject a single KYC document. */
export async function decideDocument(documentId, status, reason) {
  const response = await api.put(
    `/admin/kyc-documents/${documentId}/decision`,
    { status, reason }
  );

  return response.data;
}

const partnerService = {
  getPartnerTypes,
  getAllowedUploadTypes,
  getMyApplications,
  getApplication,
  saveApplication,
  submitApplication,
  withdrawApplication,
  uploadDocument,
  getReviewQueue,
  getApplicationForReview,
  decideApplication,
  decideDocument,
};

export default partnerService;