import { useCallback, useEffect, useState } from "react";
import {
  Building2,
  CheckCircle2,
  Loader2,
  MapPin,
  RefreshCw,
  XCircle,
} from "lucide-react";

import adminService from "../../services/adminService";

/*
 * PROPERTY VERIFICATION QUEUE  (admin approvals)
 *
 * Mirrors the KYC queue: the reviewer sees what was submitted
 * and rules on it. A rejection demands a written reason, so the
 * partner always knows what to fix; approval is silent because
 * there is nothing to correct.
 */
export default function PropertiesQueue() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const [busyId, setBusyId] = useState(null);
  const [rejectId, setRejectId] = useState(null);
  const [reason, setReason] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const queue = await adminService.getPendingProperties();
      setItems(Array.isArray(queue) ? queue : []);
    } catch (loadError) {
      setError(
        loadError?.response?.data?.message ||
          "Could not load the approval queue."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function decide(property, approved) {
    const writtenReason =
      approved || rejectId === property.propertyId
        ? reason.trim() || null
        : null;

    if (!approved && !writtenReason) return;

    setBusyId(property.propertyId);
    setError("");
    setNotice("");
    try {
      await adminService.decideProperty(
        property.propertyId,
        approved,
        writtenReason
      );
      setNotice(
        approved
          ? `${property.name} is now live.`
          : `${property.name} rejected; the partner has been notified.`
      );
      setRejectId(null);
      setReason("");
      await load();
    } catch (actionError) {
      setError(
        actionError?.response?.data?.message ||
          "Could not record this decision."
      );
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs text-slate-500">
          {items.length} propert{items.length === 1 ? "y" : "ies"} awaiting
          a decision.
        </p>
        <button
          type="button"
          onClick={load}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-700 px-3 py-2 text-xs font-semibold text-slate-300 hover:bg-slate-800 disabled:opacity-50"
        >
          {loading ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
          ) : (
            <RefreshCw className="h-3.5 w-3.5" />
          )}
          Refresh
        </button>
      </div>

      {error && (
        <p className="mt-4 rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-xs text-rose-300">
          {error}
        </p>
      )}
      {notice && (
        <p className="mt-4 rounded-xl border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-xs text-emerald-300">
          {notice}
        </p>
      )}

      {loading ? (
        <div className="mt-4 flex min-h-40 items-center justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-cyan-400" />
        </div>
      ) : items.length === 0 ? (
        <div className="mt-4 flex min-h-40 flex-col items-center justify-center gap-2 rounded-2xl border border-slate-800 bg-slate-950/60 px-5 py-8 text-center">
          <CheckCircle2 className="h-8 w-8 text-emerald-400" />
          <p className="text-sm text-slate-400">
            Nothing is awaiting property review.
          </p>
        </div>
      ) : (
        <div className="mt-4 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {items.map((property) => (
            <article
              key={property.propertyId}
              className="flex flex-col rounded-2xl border border-slate-800 bg-slate-950/60 p-5 backdrop-blur-xl"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h3 className="flex items-center gap-2 text-sm font-semibold text-white">
                    <Building2 className="h-4 w-4 shrink-0 text-cyan-400" />
                    {property.name}
                  </h3>
                  <p className="mt-1 flex items-start gap-1.5 text-xs text-slate-500">
                    <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                    <span>
                      {[property.stateName, property.address]
                        .filter(Boolean)
                        .join(" — ")}
                    </span>
                  </p>
                </div>
                <span className="inline-flex shrink-0 rounded-full border border-amber-500/30 bg-amber-500/10 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider text-amber-300">
                  {property.statusLabel || property.status}
                </span>
              </div>

              {property.description && (
                <p className="mt-3 line-clamp-3 text-xs leading-5 text-slate-400">
                  {property.description}
                </p>
              )}

              <dl className="mt-4 grid grid-cols-2 gap-2 text-[11px]">
                <div>
                  <dt className="text-slate-500">Type</dt>
                  <dd className="font-semibold text-slate-300">
                    {property.propertyType}
                  </dd>
                </div>
                <div>
                  <dt className="text-slate-500">Room types</dt>
                  <dd className="font-semibold text-slate-300">
                    {property.roomTypeCount}
                  </dd>
                </div>
                <div className="col-span-2">
                  <dt className="text-slate-500">Submitted</dt>
                  <dd className="font-semibold text-slate-300">
                    {property.submittedAt
                      ? new Date(property.submittedAt).toLocaleString()
                      : "—"}
                  </dd>
                </div>
              </dl>
              {rejectId === property.propertyId ? (
                <div className="mt-4">
                  <label className="block text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                    Rejection reason (required)
                    <textarea
                      value={reason}
                      onChange={(event) => setReason(event.target.value)}
                      maxLength={500}
                      rows={3}
                      placeholder="What must the partner fix?"
                      className="mt-1.5 w-full rounded-xl border border-slate-800 bg-slate-900 px-3 py-2 text-xs normal-case tracking-normal text-white outline-none placeholder:text-slate-600 focus:border-rose-400"
                    />
                  </label>
                  <div className="mt-3 flex gap-2">
                    <button
                      type="button"
                      onClick={() => decide(property, false)}
                      disabled={
                        busyId === property.propertyId || !reason.trim()
                      }
                      className="inline-flex items-center gap-1.5 rounded-xl bg-rose-500 px-3 py-2 text-xs font-semibold text-white hover:bg-rose-400 disabled:opacity-50"
                    >
                      {busyId === property.propertyId ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <XCircle className="h-3.5 w-3.5" />
                      )}
                      Confirm rejection
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setRejectId(null);
                        setReason("");
                      }}
                      className="rounded-xl border border-slate-700 px-3 py-2 text-xs font-semibold text-slate-300 hover:bg-slate-800"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              ) : (
                <div className="mt-4 flex gap-2">
                  <button
                    type="button"
                    onClick={() => decide(property, true)}
                    disabled={busyId === property.propertyId}
                    className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-emerald-500 px-3 py-2 text-xs font-semibold text-slate-950 hover:bg-emerald-400 disabled:opacity-50"
                  >
                    {busyId === property.propertyId ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <CheckCircle2 className="h-3.5 w-3.5" />
                    )}
                    Approve
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setRejectId(property.propertyId);
                      setReason("");
                    }}
                    disabled={busyId === property.propertyId}
                    className="inline-flex items-center gap-1.5 rounded-xl border border-rose-500/30 px-3 py-2 text-xs font-semibold text-rose-300 hover:bg-rose-500/10 disabled:opacity-50"
                  >
                    <XCircle className="h-3.5 w-3.5" />
                    Reject
                  </button>
                </div>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}