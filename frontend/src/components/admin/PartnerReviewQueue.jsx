import { useCallback, useEffect, useState } from "react";
import {
  AlertCircle,
  CheckCircle2,
  FileText,
  Loader2,
  ShieldCheck,
  XCircle,
} from "lucide-react";

import partnerService from "../../services/partnerService";

const TYPE_LABEL = {
  HOTEL: "Hotel",
  GUIDE: "Guide",
  CAB: "Cab",
};

const DOCUMENT_TONE = {
  VERIFIED: "border-emerald-500/30 bg-emerald-500/10 text-emerald-300",
  REJECTED: "border-rose-500/30 bg-rose-500/10 text-rose-300",
  UPLOADED: "border-amber-500/30 bg-amber-500/10 text-amber-300",
  UNDER_REVIEW: "border-amber-500/30 bg-amber-500/10 text-amber-300",
  NOT_UPLOADED: "border-white/10 bg-white/5 text-slate-500",
};

/*
 * ============================================================
 * PARTNER KYC REVIEW QUEUE
 *
 * Replaces the former hard-coded "0" placeholder with the live review
 * queue. Two things can be decided here:
 *
 *   1. each individual document  - approve a valid identity proof
 *      while rejecting an unreadable licence in the same pass
 *   2. the whole application    - this is the ONLY action in the
 *      platform that grants a partner role
 *
 * Rejecting always requires a reason, enforced server-side as well, so
 * an applicant is never left guessing what to fix.
 * ============================================================
 */

export default function PartnerReviewQueue() {
  const [applications, setApplications] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const selected =
    applications.find(
      (application) => application.applicationId === selectedId
    ) ?? null;

  const load = useCallback(async () => {
    try {
      const queue = await partnerService.getReviewQueue();

      setApplications(queue);

      /*
       * Keep a selection alive across refreshes, but never leave the
       * panel showing an application that is no longer in the queue.
       */
      setSelectedId((current) => {
        if (current && queue.some((item) => item.applicationId === current)) {
          return current;
        }

        return queue[0]?.applicationId ?? null;
      });
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Could not load the partner review queue."
      );
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  function resetFeedback() {
    setError("");
    setNotice("");
  }

  async function handleDocumentDecision(documentId, status) {
    if (!selected) return;

    try {
      setBusy(true);
      resetFeedback();

      await partnerService.decideDocument(documentId, status, reason);

      setNotice(
        status === "VERIFIED"
          ? "Document verified."
          : "Document rejected."
      );
      setReason("");

      await load();
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Could not record that document decision."
      );
    } finally {
      setBusy(false);
    }
  }

  async function handleApplicationDecision(approved) {
    if (!selected) return;

    try {
      setBusy(true);
      resetFeedback();

      await partnerService.decideApplication(
        selected.applicationId,
        approved,
        reason
      );

      setNotice(
        approved
          ? "Approved. The partner role has been granted."
          : "Rejected. The applicant can now correct and resubmit."
      );
      setReason("");
      setSelectedId(null);

      await load();
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Could not record that decision."
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.3fr)]">
      {/* ----------------------------------------------------
          Queue
         ---------------------------------------------------- */}
      <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-400">
            Awaiting review
          </h3>

          <button
            type="button"
            onClick={load}
            className="text-xs font-semibold text-cyan-400 hover:text-cyan-300"
          >
            Refresh
          </button>
        </div>

        {applications.length === 0 ? (
          <p className="mt-6 text-sm text-slate-500">
            No partner applications are waiting for a decision.
          </p>
        ) : (
          <ul className="mt-4 space-y-2">
            {applications.map((application) => (
              <li key={application.applicationId}>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedId(application.applicationId);
                    resetFeedback();
                  }}
                  className={`w-full rounded-xl border p-3 text-left transition ${
                    selectedId === application.applicationId
                      ? "border-cyan-400/50 bg-cyan-400/5"
                      : "border-slate-800 hover:border-slate-700"
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm font-semibold text-white">
                      {application.applicantName}
                    </span>

                    <span className="rounded-full border border-slate-700 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-slate-300">
                      {TYPE_LABEL[application.partnerType]}
                    </span>
                  </div>

                  <p className="mt-1 truncate text-xs text-slate-400">
                    {application.businessName || "No business name"}
                  </p>

                  <p className="mt-1 text-[11px] text-slate-500">
                    {application.statusLabel}
                    {application.submittedAt
                      ? ` - ${new Date(
                          application.submittedAt
                        ).toLocaleDateString()}`
                      : ""}
                    {` - ${application.uploadedDocumentCount}/${application.mandatoryDocumentCount} docs`}
                  </p>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* ----------------------------------------------------
          Detail
         ---------------------------------------------------- */}
      <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5">
        {!selected ? (
          <p className="text-sm text-slate-500">
            Select an application to review it.
          </p>
        ) : (
          <>
            <h3 className="text-base font-bold text-white">
              {selected.businessName || "Unnamed applicant"}
            </h3>

            <p className="mt-1 text-xs text-slate-500">
              {selected.applicantEmail}
            </p>

            {error && (
              <p className="mt-4 flex gap-2 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs text-rose-300">
                <AlertCircle className="h-4 w-4 shrink-0" />
                {error}
              </p>
            )}

            {notice && (
              <p className="mt-4 flex gap-2 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-3 text-xs text-emerald-300">
                <CheckCircle2 className="h-4 w-4 shrink-0" />
                {notice}
              </p>
            )}

            {/* Business details */}
            <dl className="mt-5 grid gap-2 text-xs sm:grid-cols-2">
              <Detail label="Type" value={TYPE_LABEL[selected.partnerType]} />
              <Detail label="Status" value={selected.statusLabel} />
              <Detail label="Phone" value={selected.contactPhone || "-"} />
              <Detail label="City" value={selected.city || "-"} />
              <Detail label="State" value={selected.state || "-"} />
              <Detail
                label="Tax ID"
                value={selected.taxIdentifier || "-"}
              />
              <Detail
                label="Bank account"
                value={selected.bankAccountNumber || "-"}
              />
              <Detail label="IFSC" value={selected.bankIfsc || "-"} />
            </dl>

            {selected.additionalInfo && (
              <div className="mt-4">
                <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                  Details
                </p>
                <p className="mt-1 text-xs leading-5 text-slate-300">
                  {selected.additionalInfo}
                </p>
              </div>
            )}

            {/* Documents */}
            <p className="mt-6 text-[11px] font-semibold uppercase tracking-wider text-slate-500">
              Verification checklist
            </p>

            <ul className="mt-3 space-y-2">
              {selected.documents.map((document) => (
                <li
                  key={document.documentCode}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-800 p-3"
                >
                  <div className="min-w-0">
                    <p className="flex items-center gap-2 text-xs font-semibold text-slate-200">
                      <FileText className="h-3.5 w-3.5" />
                      {document.displayName}
                      {document.mandatory && (
                        <span className="text-cyan-400">*</span>
                      )}
                    </p>

                    <p className="mt-1 text-[11px] text-slate-500">
                      {document.fileName || "No file uploaded"}
                    </p>
                  </div>

                  <span
                    className={`rounded-full border px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider ${
                      DOCUMENT_TONE[document.status] ??
                      DOCUMENT_TONE.NOT_UPLOADED
                    }`}
                  >
                    {document.status.replace(/_/g, " ")}
                  </span>

                  {document.fileName && (
                    <div className="flex gap-2">
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() =>
                          handleDocumentDecision(
                            document.documentId,
                            "VERIFIED"
                          )
                        }
                        className="rounded-lg border border-emerald-500/30 px-2.5 py-1 text-[11px] font-semibold text-emerald-300 transition hover:bg-emerald-500/10 disabled:opacity-50"
                      >
                        Verify
                      </button>

                      <button
                        type="button"
                        disabled={busy}
                        onClick={() =>
                          handleDocumentDecision(
                            document.documentId,
                            "REJECTED"
                          )
                        }
                        className="rounded-lg border border-rose-500/30 px-2.5 py-1 text-[11px] font-semibold text-rose-300 transition hover:bg-rose-500/10 disabled:opacity-50"
                      >
                        Reject
                      </button>
                    </div>
                  )}
                </li>
              ))}
            </ul>

            {/* Decision */}
            <div className="mt-6 border-t border-slate-800 pt-5">
              <label className="block text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                Reason (required to reject)
              </label>

              <textarea
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                rows={3}
                placeholder="e.g. Property ownership document is unclear."
                className="mt-2 w-full rounded-xl border border-slate-800 bg-slate-900 px-3 py-2.5 text-xs text-white outline-none transition placeholder:text-slate-600 focus:border-cyan-400"
              />

              <div className="mt-4 flex flex-wrap gap-3">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => handleApplicationDecision(true)}
                  className="inline-flex items-center gap-2 rounded-xl bg-emerald-500 px-4 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400 disabled:opacity-60"
                >
                  {busy ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <ShieldCheck className="h-4 w-4" />
                  )}
                  Approve & activate
                </button>

                <button
                  type="button"
                  disabled={busy}
                  onClick={() => handleApplicationDecision(false)}
                  className="inline-flex items-center gap-2 rounded-xl border border-rose-500/30 px-4 py-2.5 text-sm font-semibold text-rose-300 transition hover:bg-rose-500/10 disabled:opacity-60"
                >
                  <XCircle className="h-4 w-4" />
                  Reject
                </button>
              </div>

              <p className="mt-3 text-[11px] leading-5 text-slate-500">
                Approving grants the partner role immediately. The
                applicant keeps traveler access and can hold other
                partner roles as well.
              </p>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

function Detail({ label, value }) {
  return (
    <div className="flex justify-between gap-2 border-b border-slate-800/60 pb-1.5">
      <dt className="text-slate-500">{label}</dt>
      <dd className="font-semibold text-slate-200">{value}</dd>
    </div>
  );
}