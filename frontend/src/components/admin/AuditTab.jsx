import { useCallback, useEffect, useState } from "react";
import {
  Database,
  Loader2,
  RefreshCw,
  ScrollText,
} from "lucide-react";

import adminService from "../../services/adminService";

/*
 * IMMUTABLE AUDIT LOG VIEWER  (FR-30)
 *
 * Read-only by design: there is no edit or delete control here
 * because there is no endpoint that could honour one. Each row
 * shows who acted, what they did, on which entity, from which
 * IP, and when.
 */

const ACTION_STYLES = {
  USER_SUSPENDED: "border-rose-500/30 bg-rose-500/10 text-rose-300",
  USER_REACTIVATED:
    "border-emerald-500/30 bg-emerald-500/10 text-emerald-300",
  REFUND_APPROVED: "border-amber-500/30 bg-amber-500/10 text-amber-300",
  REFUND_REJECTED: "border-rose-500/30 bg-rose-500/10 text-rose-300",
  PROPERTY_APPROVED:
    "border-emerald-500/30 bg-emerald-500/10 text-emerald-300",
  PROPERTY_REJECTED: "border-rose-500/30 bg-rose-500/10 text-rose-300",
  PROPERTY_SUSPENDED: "border-rose-500/30 bg-rose-500/10 text-rose-300",
  PARTNER_APPLICATION_APPROVED:
    "border-emerald-500/30 bg-emerald-500/10 text-emerald-300",
  PARTNER_APPLICATION_REJECTED:
    "border-rose-500/30 bg-rose-500/10 text-rose-300",
};

function actionStyle(action) {
  return (
    ACTION_STYLES[action] ||
    "border-cyan-500/30 bg-cyan-500/10 text-cyan-300"
  );
}

function when(value) {
  if (!value) return "—";
  return new Date(value).toLocaleString();
}

export default function AuditTab() {
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const log = await adminService.getAuditLog();
      setEntries(Array.isArray(log) ? log : []);
    } catch (loadError) {
      setError(
        loadError?.response?.data?.message ||
          "Could not load the audit trail."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <section className="rounded-2xl border border-slate-800 bg-slate-950/60 backdrop-blur-xl">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 px-5 py-4">
        <div>
          <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-slate-400">
            <ScrollText className="h-4 w-4 text-purple-400" />
            Audit &amp; Risk Alerts
          </h2>
          <p className="mt-1 text-xs text-slate-500">
            Append-only record of high-privilege actions. Newest first.
          </p>
        </div>
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
        <p className="m-5 rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-xs text-rose-300">
          {error}
        </p>
      )}

      {loading ? (
        <div className="flex min-h-40 items-center justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-purple-400" />
        </div>
      ) : entries.length === 0 ? (
        <div className="flex min-h-40 flex-col items-center justify-center gap-2 px-5 py-8 text-center">
          <Database className="h-8 w-8 text-slate-600" />
          <p className="text-sm text-slate-400">
            No privileged actions recorded yet.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-800 text-[10px] uppercase tracking-wider text-slate-500">
                <th className="px-5 py-3 font-semibold">Time</th>
                <th className="px-3 py-3 font-semibold">Admin</th>
                <th className="px-3 py-3 font-semibold">Action</th>
                <th className="px-3 py-3 font-semibold">Entity</th>
                <th className="px-3 py-3 font-semibold">Detail</th>
                <th className="px-3 py-3 font-semibold">Source IP</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr
                  key={entry.auditId}
                  className="border-b border-slate-800/60 align-top hover:bg-slate-900/40"
                >
                  <td className="whitespace-nowrap px-5 py-3 text-slate-400">
                    {when(entry.createdAt)}
                  </td>
                  <td className="px-3 py-3">
                    <div className="font-semibold text-white">
                      {entry.actorName || "Unknown"}
                    </div>
                    <div className="text-[11px] text-slate-500">
                      {entry.actorEmail}
                    </div>
                  </td>
                  <td className="px-3 py-3">
                    <span
                      className={`inline-flex rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider ${actionStyle(
                        entry.action
                      )}`}
                    >
                      {entry.action.replaceAll("_", " ")}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-3 py-3 text-slate-300">
                    {entry.entityType}
                    {entry.entityId != null && (
                      <span className="text-slate-500">
                        {" "}
                        #{entry.entityId}
                      </span>
                    )}
                  </td>
                  <td className="max-w-md px-3 py-3 text-slate-400">
                    {entry.detail || "—"}
                  </td>
                  <td className="whitespace-nowrap px-3 py-3 font-mono text-[11px] text-slate-500">
                    {entry.ipAddress || "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
