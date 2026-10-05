import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Clock,
  FileUp,
  Loader2,
  Send,
  XCircle,
} from "lucide-react";

import partnerService from "../../services/partnerService";
import { getDefaultRouteForRole } from "../../components/ProtectedRoute";
import { useAuth } from "../../context/useAuth";

const EMPTY_FORM = {
  businessName: "",
  contactPhone: "",
  addressLine: "",
  city: "",
  state: "",
  postalCode: "",
  taxIdentifier: "",
  additionalInfo: "",
  bankAccountName: "",
  bankAccountNumber: "",
  bankIfsc: "",
};

const STATUS_TONE = {
  DRAFT: {
    className: "border-white/15 bg-white/5 text-white/70",
    icon: FileUp,
    title: "Draft",
    body: "Fill in your details and upload the required documents, then submit for review.",
  },
  PENDING_REVIEW: {
    className: "border-amber-500/30 bg-amber-500/10 text-amber-300",
    icon: Clock,
    title: "Under review",
    body: "A Travel Buddy admin is reviewing your application. We will notify you once a decision is made.",
  },
  UNDER_REVIEW: {
    className: "border-amber-500/30 bg-amber-500/10 text-amber-300",
    icon: Clock,
    title: "Under review",
    body: "A Travel Buddy admin is reviewing your application.",
  },
  PENDING_CORRECTION: {
    className: "border-amber-500/30 bg-amber-500/10 text-amber-300",
    icon: AlertCircle,
    title: "Changes requested",
    body: "Update the details below and resubmit for review.",
  },
  REJECTED: {
    className: "border-rose-500/30 bg-rose-500/10 text-rose-300",
    icon: XCircle,
    title: "Application rejected",
    body: "Correct the information below and resubmit your application.",
  },
  APPROVED: {
    className: "border-emerald-500/30 bg-emerald-500/10 text-emerald-300",
    icon: CheckCircle2,
    title: "Approved",
    body: "Your partner role is active. You can now use the partner portal.",
  },
  WITHDRAWN: {
    className: "border-white/15 bg-white/5 text-white/50",
    icon: XCircle,
    title: "Withdrawn",
    body: "This application was withdrawn. Contact support if you want to reapply.",
  },
};

const PORTAL_BY_TYPE = {
  HOTEL: "ROLE_HOTEL_PARTNER",
  GUIDE: "ROLE_GUIDE_PARTNER",
  CAB: "ROLE_CAB_PARTNER",
};

/*
 * ============================================================
 * PARTNER APPLICATION
 *
 * Multi-step applicant flow:
 *
 *   details -> documents -> review summary -> submit
 *
 * canEdit / canSubmit are supplied by the backend, so the browser never
 * re-implements the state machine and cannot drift out of sync with it.
 * ============================================================
 */

export default function PartnerApplication() {
  const { partnerType } = useParams();
  const navigate = useNavigate();
  const { user, refreshProfile } = useAuth();

  const [application, setApplication] = useState(null);
  const [requirements, setRequirements] = useState([]);
  const [allowedTypes, setAllowedTypes] = useState([]);

  const [form, setForm] = useState(EMPTY_FORM);
  const [step, setStep] = useState(0);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [uploadingCode, setUploadingCode] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const fileInputs = useRef({});

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        setLoading(true);

        const [data, catalogue, limits] = await Promise.all([
          partnerService.getApplication(partnerType),
          partnerService.getPartnerTypes(),
          partnerService.getAllowedUploadTypes(),
        ]);

        if (!active) return;

        setApplication(data);
        setForm({
          businessName: data.businessName ?? "",
          contactPhone: data.contactPhone ?? "",
          addressLine: data.addressLine ?? "",
          city: data.city ?? "",
          state: data.state ?? "",
          postalCode: data.postalCode ?? "",
          taxIdentifier: data.taxIdentifier ?? "",
          additionalInfo: data.additionalInfo ?? "",
          bankAccountName: data.bankAccountName ?? "",
          bankAccountNumber: data.bankAccountNumber ?? "",
          bankIfsc: data.bankIfsc ?? "",
        });

        setRequirements(
          catalogue.find((item) => item.type === partnerType)
            ?.requirements ?? []
        );

        setAllowedTypes(limits.allowedContentTypes ?? []);
      } catch (err) {
        if (active) {
          setError(
            err.response?.data?.message ||
              "Could not load your application."
          );
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    load();

    return () => {
      active = false;
    };
  }, [partnerType]);

  /*
   * An approved partner should be sent straight into their portal
   * instead of staring at a read-only form.
   */
  useEffect(() => {
    if (application?.status === "APPROVED") {
      navigate(
        getDefaultRouteForRole(PORTAL_BY_TYPE[partnerType]),
        { replace: true }
      );
    }
  }, [application, partnerType, navigate]);

  /*
   * `application.documents` is a stable reference between fetches, so it
   * is memoized on its own first. Without this, the `?? []` fallback
   * would build a fresh array on every render and make the derived
   * values below recompute unconditionally.
   */
  const documents = useMemo(
    () => application?.documents ?? [],
    [application]
  );

  const missingMandatory = useMemo(
    () =>
      documents.filter(
        (document) =>
          document.mandatory && document.status === "NOT_UPLOADED"
      ),
    [documents]
  );

  const requiredComplete =
    missingMandatory.length === 0 && documents.length > 0;

  function handleChange(event) {
    const { name, value } = event.target;

    setForm((previous) => ({
      ...previous,
      [name]: value,
    }));
  }

  async function handleSaveDetails() {
    try {
      setSaving(true);
      setError("");

      const saved = await partnerService.saveApplication(
        partnerType,
        form
      );

      setApplication(saved);
      setNotice("Details saved.");
    } catch (err) {
      setError(
        err.response?.data?.message || "Could not save your details."
      );
    } finally {
      setSaving(false);
    }
  }

  async function handleFileSelected(documentCode, file) {
    if (!file) return;

    try {
      setUploadingCode(documentCode);
      setError("");
      setNotice("");

      await partnerService.uploadDocument(
        partnerType,
        documentCode,
        file
      );

      const refreshed =
        await partnerService.getApplication(partnerType);

      setApplication(refreshed);
      setNotice("Document uploaded.");
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Could not upload that document."
      );
    } finally {
      setUploadingCode(null);
    }
  }

  async function handleSubmit() {
    try {
      setSubmitting(true);
      setError("");

      await handleSaveDetails();
      await partnerService.submitApplication(partnerType);

      setApplication(
        await partnerService.getApplication(partnerType)
      );

      setNotice(
        "Application submitted. You will hear from us once it is reviewed."
      );

      /*
       * Approval grants the role later, so refresh the cached profile so
       * the navbar reflects the new state as soon as it changes.
       */
      await refreshProfile();
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Could not submit your application."
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function handleWithdraw() {
    try {
      setSubmitting(true);
      setError("");

      await partnerService.withdrawApplication(partnerType);

      setApplication(
        await partnerService.getApplication(partnerType)
      );
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Could not withdraw your application."
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-[calc(100vh-72px)] items-center justify-center bg-slate-950 text-white">
        <Loader2 className="h-8 w-8 animate-spin text-cyan-400" />
      </div>
    );
  }

  if (!application) {
    return (
      <div className="flex min-h-[calc(100vh-72px)] items-center justify-center bg-slate-950 px-5 text-white">
        <p className="text-sm text-rose-300">
          {error || "Application not found."}
        </p>
      </div>
    );
  }

  const tone = STATUS_TONE[application.status] ?? STATUS_TONE.DRAFT;
  const ToneIcon = tone.icon;
  const canEdit = application.canEdit;

  const STEPS = ["Business details", "Documents", "Review & submit"];

  return (
    <div className="min-h-[calc(100vh-72px)] bg-slate-950 px-5 py-12 text-white">
      <div className="mx-auto max-w-4xl">
        <button
          type="button"
          onClick={() => navigate("/become-a-partner")}
          className="inline-flex items-center gap-2 text-sm text-slate-400 transition hover:text-white"
        >
          <ArrowLeft className="h-4 w-4" />
          All partner types
        </button>

        <h1 className="mt-5 text-3xl font-black tracking-tight">
          {partnerType.charAt(0) + partnerType.slice(1).toLowerCase()}{" "}
          Partner Application
        </h1>

        {/* ------------------------------------------------------
            Status banner
           ------------------------------------------------------ */}
        <div
          className={`mt-6 flex gap-3 rounded-2xl border p-4 ${tone.className}`}
        >
          <ToneIcon className="mt-0.5 h-5 w-5 shrink-0" />

          <div>
            <p className="text-sm font-bold">{tone.title}</p>
            <p className="mt-1 text-sm opacity-90">{tone.body}</p>

            {application.rejectionReason && (
              <p className="mt-2 text-xs leading-5">
                Reason: {application.rejectionReason}
              </p>
            )}

            {application.submittedAt && (
              <p className="mt-2 text-xs opacity-70">
                Submitted{" "}
                {new Date(
                  application.submittedAt
                ).toLocaleDateString()}
              </p>
            )}
          </div>
        </div>

        {error && (
          <div className="mt-4 rounded-2xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-300">
            {error}
          </div>
        )}

        {notice && (
          <div className="mt-4 rounded-2xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-300">
            {notice}
          </div>
        )}

        {/* ------------------------------------------------------
            Step indicator
           ------------------------------------------------------ */}
        {canEdit && (
          <div className="mt-8 flex flex-wrap gap-2">
            {STEPS.map((label, index) => (
              <button
                key={label}
                type="button"
                onClick={() => setStep(index)}
                className={`rounded-full px-4 py-2 text-xs font-semibold transition ${
                  step === index
                    ? "bg-cyan-400 text-slate-950"
                    : "border border-white/10 text-slate-400 hover:border-white/25"
                }`}
              >
                {index + 1}. {label}
              </button>
            ))}
          </div>
        )}

        {/* ------------------------------------------------------
            Step 1 - details
           ------------------------------------------------------ */}
        {canEdit && step === 0 && (
          <section className="mt-6 rounded-3xl border border-white/10 bg-white/[0.03] p-6">
            <h2 className="text-lg font-bold">Business details</h2>

            <p className="mt-1 text-sm text-slate-400">
              We pre-filled what we already know. Complete the rest.
            </p>

            <div className="mt-6 grid gap-4 sm:grid-cols-2">
              {requirements.map((requirement) => (
                <div
                  key={requirement.field}
                  className={
                    requirement.field === "additionalInfo"
                      ? "sm:col-span-2"
                      : ""
                  }
                >
                  <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-slate-400">
                    {requirement.label}
                    {requirement.required && (
                      <span className="ml-1 text-cyan-400">*</span>
                    )}
                  </label>

                  <input
                    name={requirement.field}
                    required={requirement.required}
                    value={form[requirement.field] ?? ""}
                    onChange={handleChange}
                    className="w-full rounded-xl border border-white/10 bg-black/20 px-4 py-3 text-sm text-white outline-none transition placeholder:text-white/25 focus:border-cyan-400"
                  />
                </div>
              ))}
            </div>

            <div className="mt-6 flex flex-wrap gap-3">
              <button
                type="button"
                onClick={handleSaveDetails}
                disabled={saving}
                className="rounded-xl border border-white/15 px-5 py-2.5 text-sm font-semibold transition hover:border-white/30 disabled:opacity-60"
              >
                {saving ? "Saving..." : "Save details"}
              </button>

              <button
                type="button"
                onClick={() => setStep(1)}
                className="rounded-xl bg-cyan-400 px-5 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-cyan-300"
              >
                Continue to documents
              </button>
            </div>
          </section>
        )}

        {/* ------------------------------------------------------
            Step 2 - documents
           ------------------------------------------------------ */}
        {canEdit && step === 1 && (
          <section className="mt-6 rounded-3xl border border-white/10 bg-white/[0.03] p-6">
            <h2 className="text-lg font-bold">Document verification</h2>

            <p className="mt-1 text-sm text-slate-400">
              {application.uploadedDocumentCount} of{" "}
              {application.mandatoryDocumentCount} required documents
              uploaded.
            </p>

            <div className="mt-6 space-y-3">
              {documents.map((document) => (
                <div
                  key={document.documentCode}
                  className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-white/10 bg-black/20 p-4"
                >
                  <div className="min-w-0">
                    <p className="text-sm font-semibold">
                      {document.displayName}
                      {document.mandatory && (
                        <span className="ml-1 text-cyan-400">*</span>
                      )}
                    </p>

                    <p className="mt-0.5 text-xs text-slate-500">
                      {document.status.replace(/_/g, " ")}
                      {document.fileName
                        ? ` - ${document.fileName}`
                        : ""}
                    </p>

                    {document.rejectionReason && (
                      <p className="mt-1 text-xs text-rose-300">
                        {document.rejectionReason}
                      </p>
                    )}
                  </div>

                  {canEdit && (
                    <>
                      <input
                        ref={(element) => {
                          fileInputs.current[
                            document.documentCode
                          ] = element;
                        }}
                        type="file"
                        accept=".pdf,.jpg,.jpeg,.png,.webp"
                        className="hidden"
                        onChange={(event) =>
                          handleFileSelected(
                            document.documentCode,
                            event.target.files?.[0]
                          )
                        }
                      />

                      <button
                        type="button"
                        disabled={
                          uploadingCode ===
                          document.documentCode
                        }
                        onClick={() =>
                          fileInputs.current[
                            document.documentCode
                          ]?.click()
                        }
                        className="rounded-xl border border-white/15 px-4 py-2 text-xs font-semibold transition hover:border-cyan-400/50 hover:text-cyan-300 disabled:opacity-60"
                      >
                        {uploadingCode ===
                        document.documentCode
                          ? "Uploading..."
                          : document.status === "NOT_UPLOADED"
                            ? "Upload"
                            : "Replace"}
                      </button>
                    </>
                  )}
                </div>
              ))}
            </div>

            {allowedTypes.length > 0 && (
              <p className="mt-4 text-xs text-slate-500">
                Accepted formats:{" "}
                {allowedTypes
                  .map((type) => type.split("/")[1])
                  .join(", ")}{" "}
                - up to 10 MB each.
              </p>
            )}

            <div className="mt-6 flex flex-wrap gap-3">
              <button
                type="button"
                onClick={() => setStep(0)}
                className="rounded-xl border border-white/15 px-5 py-2.5 text-sm font-semibold transition hover:border-white/30"
              >
                Back
              </button>

              <button
                type="button"
                onClick={() => setStep(2)}
                className="rounded-xl bg-cyan-400 px-5 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-cyan-300"
              >
                Review application
              </button>
            </div>
          </section>
        )}

        {/* ------------------------------------------------------
            Step 3 - review
           ------------------------------------------------------ */}
        {canEdit && step === 2 && (
          <section className="mt-6 rounded-3xl border border-white/10 bg-white/[0.03] p-6">
            <h2 className="text-lg font-bold">
              Application summary
            </h2>

            <dl className="mt-5 space-y-3 text-sm">
              <SummaryRow
                label="Partner type"
                value={application.partnerType}
              />
              <SummaryRow
                label="Business name"
                value={application.businessName || "Not provided"}
              />
              <SummaryRow
                label="Contact phone"
                value={application.contactPhone || "Not provided"}
              />
              <SummaryRow
                label="City"
                value={application.city || "Not provided"}
              />
              <SummaryRow
                label="Documents"
                value={`${application.uploadedDocumentCount} of ${application.mandatoryDocumentCount} uploaded`}
              />
            </dl>

            {!requiredComplete && (
              <p className="mt-5 rounded-xl border border-amber-500/20 bg-amber-500/10 p-3 text-xs leading-5 text-amber-200">
                Still required:{" "}
                {missingMandatory
                  .map((document) => document.displayName)
                  .join(", ")}
              </p>
            )}

            <p className="mt-5 text-xs leading-5 text-slate-500">
              By submitting you confirm the information above is accurate
              and that Travel Buddy may verify the uploaded documents.
            </p>

            <div className="mt-6 flex flex-wrap gap-3">
              <button
                type="button"
                onClick={() => setStep(1)}
                className="rounded-xl border border-white/15 px-5 py-2.5 text-sm font-semibold transition hover:border-white/30"
              >
                Back
              </button>

              <button
                type="button"
                onClick={handleSubmit}
                disabled={
                  submitting || !application.canSubmit
                }
                className="inline-flex items-center gap-2 rounded-xl bg-cyan-400 px-5 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {submitting ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Send className="h-4 w-4" />
                )}
                Submit for review
              </button>

              <button
                type="button"
                onClick={handleWithdraw}
                disabled={submitting}
                className="rounded-xl border border-white/10 px-5 py-2.5 text-sm font-semibold text-slate-400 transition hover:border-rose-500/40 hover:text-rose-300 disabled:opacity-60"
              >
                Withdraw
              </button>
            </div>
          </section>
        )}

        {/* ------------------------------------------------------
            Read-only view while under review
           ------------------------------------------------------ */}
        {!canEdit && (
          <section className="mt-6 rounded-3xl border border-white/10 bg-white/[0.03] p-6">
            <h2 className="text-lg font-bold">
              Application details
            </h2>

            <dl className="mt-5 space-y-3 text-sm">
              <SummaryRow
                label="Business name"
                value={application.businessName || "Not provided"}
              />
              <SummaryRow
                label="City"
                value={application.city || "Not provided"}
              />
              <SummaryRow
                label="Documents"
                value={`${application.uploadedDocumentCount} of ${application.mandatoryDocumentCount} uploaded`}
              />
            </dl>

            {user && (
              <p className="mt-6 text-xs text-slate-500">
                Need to change something? Contact Travel Buddy support
                while your application is under review.
              </p>
            )}
          </section>
        )}
      </div>
    </div>
  );
}

function SummaryRow({ label, value }) {
  return (
    <div className="flex flex-wrap justify-between gap-2 border-b border-white/5 pb-2">
      <dt className="text-slate-400">{label}</dt>
      <dd className="font-semibold">{value}</dd>
    </div>
  );
}