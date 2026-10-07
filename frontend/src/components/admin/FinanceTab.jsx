import { useCallback, useEffect, useState } from "react";
import {
  ChevronLeft,
  ChevronRight,
  Loader2,
  Receipt,
  RefreshCw,
} from "lucide-react";

import adminService from "../../services/adminService";
import FinanceRefundDesk from "./FinanceRefundDesk";

/*
 * PAYMENTS & SETTLEMENTS  (FR-33)
 *
 * The ledger is every booking that moved (or failed to move)
 * money; the refund desk below it authorizes pending refunds.
 * Approval authorizes only -- no gateway disbursement exists
 * yet, and the UI must never imply funds were sent.
 */

const STATUS_OPTIONS = ["", "UNPAID", "PAID", "REFUNDED"];

function money(amount, currency) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: currency || "INR",
    maximumFractionDigits: 2,
  }).format(Number(amount || 0));
}

function paymentBadge(status) {
  if (status === "PAID")
    return "border-emerald-500/30 bg-emerald-500/10 text-emerald-300";
  if (status === "REFUNDED")
    return "border-amber-500/30 bg-amber-500/10 text-amber-300";
  return "border-rose-500/30 bg-rose-500/10 text-rose-300";
}

const PAGE_SIZE = 25;

export default function FinanceTab() {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [ledger, setLedger] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async (nextStatus, nextPage) => {
    setLoading(true);
    setError("");
    try {
      const data = await adminService.getLedger({
        paymentStatus: nextStatus || undefined,
        page: nextPage,
        size: PAGE_SIZE,
      });
      setLedger(data);
    } catch (loadError) {
      setError(
        loadError?.response?.data?.message ||
          "Could not load the payment ledger."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(status, page);
  }, [load, status, page]);

  const totalPages = ledger?.totalPages || 0;

  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-slate-800 bg-slate-950/60 backdrop-blur-xl">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 px-5 py-4">
          <div>
            <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-slate-400">
              <Receipt className="h-4 w-4 text-emerald-400" />
              Refund &amp; Ledger Matrix
            </h2>
            <p className="mt-1 text-xs text-slate-500">
              {ledger
                ? `${ledger.totalElements.toLocaleString()} booking row${
                    ledger.totalElements === 1 ? "" : "s"
                  }`
                : "Loading…"}
            </p>
          </div>

          <div className="flex items-center gap-2">
            <select
              value={status}
              onChange={(event) => {
                setStatus(event.target.value);
                setPage(0);
              }}
              className="rounded-xl border border-slate-800 bg-slate-900 px-3 py-2 text-xs text-white outline-none focus:border-cyan-400"
            >
              <option value="">All payment states</option>
              {STATUS_OPTIONS.filter(Boolean).map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={() => load(status, page)}
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
        </div>

        {error && (
          <p className="m-5 rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-xs text-rose-300">
            {error}
          </p>
        )}

        {loading ? (
          <div className="flex min-h-40 items-center justify-center">
            <Loader2 className="h-6 w-6 animate-spin text-emerald-400" />
          </div>
        ) : (ledger?.rows || []).length === 0 ? (
          <p className="px-5 py-10 text-center text-sm text-slate-500">
            No bookings match this filter.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-800 text-[10px] uppercase tracking-wider text-slate-500">
                  <th className="px-5 py-3 font-semibold">Reference</th>
                  <th className="px-3 py-3 font-semibold">Guest</th>
                  <th className="px-3 py-3 font-semibold">Type</th>
                  <th className="px-3 py-3 font-semibold">Amount</th>
                  <th className="px-3 py-3 font-semibold">Payment</th>
                  <th className="px-3 py-3 font-semibold">Booking</th>
                  <th className="px-3 py-3 font-semibold">Created</th>
                </tr>
              </thead>
              <tbody>
                {(ledger?.rows || []).map((row) => (
                  <tr
                    key={row.bookingId}
                    className="border-b border-slate-800/60 hover:bg-slate-900/40"
                  >
                    <td className="whitespace-nowrap px-5 py-3 font-mono text-slate-300">
                      {row.bookingReference}
                    </td>
                    <td className="px-3 py-3 text-white">
                      {row.guestName}
                    </td>
                    <td className="px-3 py-3 text-slate-400">
                      {row.bookingType}
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 font-semibold text-white">
                      {money(row.totalAmount, row.currency)}
                    </td>
                    <td className="px-3 py-3">
                      <span
                        className={`inline-flex rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider ${paymentBadge(
                          row.paymentStatus
                        )}`}
                      >
                        {row.paymentStatus}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-slate-500">
                      {row.bookingStatus}
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 text-slate-500">
                      {row.createdAt
                        ? new Date(row.createdAt).toLocaleString()
                        : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-800 px-5 py-3">
            <button
              type="button"
              onClick={() => setPage((current) => current - 1)}
              disabled={page === 0 || loading}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 px-3 py-1.5 text-xs font-semibold text-slate-300 hover:bg-slate-800 disabled:opacity-40"
            >
              <ChevronLeft className="h-4 w-4" />
              Previous
            </button>
            <span className="text-xs text-slate-500">
              Page {page + 1} of {totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage((current) => current + 1)}
              disabled={page + 1 >= totalPages || loading}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 px-3 py-1.5 text-xs font-semibold text-slate-300 hover:bg-slate-800 disabled:opacity-40"
            >
              Next
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        )}
      </section>

      <FinanceRefundDesk />
    </div>
  );
}