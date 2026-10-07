import { useCallback, useEffect, useState } from "react";
import {
  AlertCircle,
  CheckCircle2,
  Clock3,
  Loader2,
  RefreshCw,
  ShieldAlert,
  XCircle,
} from "lucide-react";

import adminFinanceService from "../../services/adminFinanceService";

function currency(amount, code) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: code || "INR",
    maximumFractionDigits: 2,
  }).format(Number(amount || 0));
}

function RefundStatus({ status }) {
  const approved = status === "APPROVED";
  return (
    <span
      className={[
        "inline-flex rounded-full border px-2.5 py-1",
        "text-[10px] font-bold uppercase tracking-wider",
        approved
          ? "border-cyan-500/30 bg-cyan-500/10 text-cyan-300"
          : "border-amber-500/30 bg-amber-500/10 text-amber-300",
      ].join(" ")}
    >
      {status.replaceAll("_", " ")}
    </span>
  );
}

export default function FinanceRefundDesk() {
  const [refunds, setRefunds] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const selected =
    refunds.find((refund) => refund.cancellationId === selectedId) ?? null;

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const queue = await adminFinanceService.getRefundQueue();
      const items = Array.isArray(queue) ? queue : [];
      setRefunds(items);
      setSelectedId((current) =>
        items.some((item) => item.cancellationId === current)
          ? current
          : items[0]?.cancellationId ?? null
      );
    } catch (loadError) {
      setError(
        loadError?.response?.data?.message ||
          "Could not load the refund authorization queue."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function decide(approve) {
    if (!selected || (!approve && !reason.trim())) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const result = await adminFinanceService.decideRefund(
        selected.cancellationId,
        approve,
        approve ? null : reason.trim()
      );
      setNotice(
        approve
          ? `Refund authorized for ${currency(
              result.refundAmount,
              result.refundCurrency
            )}. Disbursement is still outstanding.`
          : "Refund authorization rejected and recorded."
      );
      setReason("");
      await load();
    } catch (decisionError) {
      setError(
        decisionError?.response?.data?.message ||
          "Could not record the refund decision."
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.3fr)]">
      <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-400">
            Refunds requiring action
          </h2>
          <button
            type="button"
            onClick={load}
            disabled={loading}
            className="inline-flex items-center gap-2 text-xs font-semibold text-cyan-400 hover:text-cyan-300 disabled:opacity-50"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            Refresh
          </button>
        </div>

        {loading ? (
          <div className="flex justify-center py-12">
            <Loader2 className="h-6 w-6 animate-spin text-cyan-300" />
          </div>
        ) : refunds.length === 0 ? (
          <p className="mt-6 text-sm text-slate-500">
            No pending or approved refunds are awaiting follow-up.
          </p>
        ) : (
          <ul className="mt-4 space-y-2">
            {refunds.map((refund) => (
              <li key={refund.cancellationId}>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedId(refund.cancellationId);
                    setReason("");
                    setError("");
                    setNotice("");
                  }}
                  className={[
                    "w-full rounded-xl border p-3 text-left transition",
                    selectedId === refund.cancellationId
                      ? "border-cyan-400/50 bg-cyan-400/5"
                      : "border-slate-800 hover:border-slate-700",
                  ].join(" ")}
                >
                  <div className="flex items-center justify-between gap-3">
                    <span className="truncate text-sm font-semibold text-white">
                      {refund.bookingReference || `Booking ${refund.bookingId}`}
                    </span>
                    <RefundStatus status={refund.refundStatus} />
                  </div>
                  <p className="mt-1 truncate text-xs text-slate-400">
                    {refund.guestName || "Traveler"} ·{" "}
                    {currency(refund.refundAmount, refund.refundCurrency)}
                  </p>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5">
        {error && (
          <p
            role="alert"
            className="mb-4 flex gap-2 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs text-rose-300"
          >
            <AlertCircle className="h-4 w-4 shrink-0" />
            {error}
          </p>
        )}
        {notice && (
          <p
            role="status"
            className="mb-4 flex gap-2 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-3 text-xs text-emerald-300"
          >
            <CheckCircle2 className="h-4 w-4 shrink-0" />
            {notice}
          </p>
        )}

        {!selected ? (
          <p className="text-sm text-slate-500">
            Select a refund to review its authorization status.
          </p>
        ) : (
          <>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h3 className="text-base font-bold text-white">
                  {selected.bookingReference || `Booking ${selected.bookingId}`}
                </h3>
                <p className="mt-1 text-xs text-slate-400">
                  {selected.guestName || "Traveler"} ·{" "}
                  {selected.cancellationReason?.replaceAll("_", " ") ||
                    "Cancellation"}
                </p>
              </div>
              <RefundStatus status={selected.refundStatus} />
            </div>

            <dl className="mt-5 grid gap-3 rounded-xl border border-slate-800 p-4 text-xs sm:grid-cols-2">
              <div>
                <dt className="text-slate-500">Amount requested</dt>
                <dd className="mt-1 font-semibold text-white">
                  {currency(selected.refundAmount, selected.refundCurrency)}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500">Cancelled</dt>
                <dd className="mt-1 font-semibold text-white">
                  {selected.cancelledAt
                    ? new Date(selected.cancelledAt).toLocaleString()
                    : "Unknown"}
                </dd>
              </div>
            </dl>

            <div className="mt-4 flex gap-3 rounded-xl border border-amber-400/20 bg-amber-400/5 p-3 text-xs leading-5 text-amber-100">
              <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
              Approval authorizes the refund only. No gateway disbursement is
              connected here; do not mark funds as sent. The status stays
              APPROVED until provider-confirmed processing is implemented.
            </div>

            {selected.refundStatus === "APPROVED" ? (
              <p className="mt-5 flex items-center gap-2 text-sm text-cyan-200">
                <Clock3 className="h-4 w-4" />
                Approved; awaiting provider disbursement and confirmation.
              </p>
            ) : (
              <>
                <label className="mt-5 block text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                  Rejection reason (required)
                  <textarea
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    maxLength={500}
                    rows={3}
                    placeholder="Explain why this refund authorization is rejected."
                    className="mt-2 w-full rounded-xl border border-slate-800 bg-slate-900 px-3 py-2.5 text-xs normal-case tracking-normal text-white outline-none placeholder:text-slate-600 focus:border-cyan-400"
                  />
                </label>

                <div className="mt-4 flex flex-wrap gap-3">
                  <button
                    type="button"
                    onClick={() => decide(true)}
                    disabled={busy || selected.refundStatus === "FAILED"}
                    className="inline-flex items-center gap-2 rounded-xl bg-emerald-500 px-4 py-2.5 text-sm font-semibold text-slate-950 hover:bg-emerald-400 disabled:opacity-50"
                  >
                    {busy ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <CheckCircle2 className="h-4 w-4" />
                    )}
                    Authorize refund
                  </button>
                  <button
                    type="button"
                    onClick={() => decide(false)}
                    disabled={busy || !reason.trim()}
                    className="inline-flex items-center gap-2 rounded-xl border border-rose-500/30 px-4 py-2.5 text-sm font-semibold text-rose-300 hover:bg-rose-500/10 disabled:opacity-50"
                  >
                    <XCircle className="h-4 w-4" />
                    Reject
                  </button>
                </div>
              </>
            )}
          </>
        )}
      </div>
    </section>
  );
}
