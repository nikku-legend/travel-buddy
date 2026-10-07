import { useCallback, useEffect, useState } from "react";
import {
  Ban,
  CheckCircle2,
  Eye,
  Loader2,
  RefreshCw,
  Search,
  UserCheck,
  X,
  XCircle,
} from "lucide-react";

import adminService from "../../services/adminService";

/*
 * USER MANAGEMENT  (FR-31)
 *
 * Search, inspect history, suspend with a mandatory reason,
 * reactivate. Suspension is enforced server-side at login and
 * token refresh -- this console only flips the flag.
 */

function roleBadge(role) {
  if (role === "ROLE_SUPER_ADMIN")
    return "border-purple-500/30 bg-purple-500/10 text-purple-300";
  if (role && role.includes("PARTNER"))
    return "border-cyan-500/30 bg-cyan-500/10 text-cyan-300";
  return "border-slate-600 bg-slate-800 text-slate-300";
}

function statusBadge(status) {
  if (status === "SUSPENDED")
    return "border-rose-500/30 bg-rose-500/10 text-rose-300";
  return "border-emerald-500/30 bg-emerald-500/10 text-emerald-300";
}

function when(value) {
  if (!value) return "—";
  return new Date(value).toLocaleDateString();
}

export default function UsersTab() {
  const [query, setQuery] = useState("");
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const [history, setHistory] = useState(null);
  const [historyLoading, setHistoryLoading] = useState(false);

  const [suspendTarget, setSuspendTarget] = useState(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async (search) => {
    setLoading(true);
    setError("");
    try {
      const list = await adminService.getUsers(
        (search || "").trim() || undefined
      );
      setUsers(Array.isArray(list) ? list : []);
    } catch (loadError) {
      setError(
        loadError?.response?.data?.message ||
          "Could not load users."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load("");
  }, [load]);

  function searchNow(event) {
    event.preventDefault();
    load(query);
  }

  async function openHistory(user) {
    setHistoryLoading(true);
    setError("");
    try {
      setHistory(await adminService.getUserHistory(user.userId));
    } catch (loadError) {
      setError(
        loadError?.response?.data?.message ||
          "Could not load this user's history."
      );
    } finally {
      setHistoryLoading(false);
    }
  }

  async function refreshAfterChange(changedId) {
    await load(query);

    if (history && history.user && history.user.userId === changedId) {
      const refreshed = await adminService.getUserHistory(changedId);
      setHistory(refreshed);
    }
  }

  async function confirmSuspend() {
    if (!suspendTarget || !reason.trim()) return;

    setBusy(true);
    setError("");
    setNotice("");
    try {
      await adminService.suspendUser(
        suspendTarget.userId,
        reason.trim()
      );
      setNotice(`${suspendTarget.fullName} is now suspended.`);
      setSuspendTarget(null);
      setReason("");
      await refreshAfterChange(suspendTarget.userId);
    } catch (actionError) {
      setError(
        actionError?.response?.data?.message ||
          "Could not suspend this account."
      );
    } finally {
      setBusy(false);
    }
  }

  async function reactivate(user) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await adminService.reactivateUser(user.userId);
      setNotice(`${user.fullName} has been reactivated.`);
      await refreshAfterChange(user.userId);
    } catch (actionError) {
      setError(
        actionError?.response?.data?.message ||
          "Could not reactivate this account."
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section>
      <div className="flex flex-wrap items-center gap-3">
        <form
          onSubmit={searchNow}
          className="flex flex-1 items-center gap-2"
        >
          <div className="relative min-w-52 flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search by name, email or phone…"
              className="w-full rounded-xl border border-slate-800 bg-slate-950 py-2.5 pl-9 pr-3 text-sm text-white outline-none placeholder:text-slate-600 focus:border-cyan-400"
            />
          </div>
          <button
            type="submit"
            className="rounded-xl bg-cyan-500 px-4 py-2.5 text-sm font-semibold text-slate-950 hover:bg-cyan-400"
          >
            Search
          </button>
        </form>
        <button
          type="button"
          onClick={() => load(query)}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-700 px-3 py-2.5 text-xs font-semibold text-slate-300 hover:bg-slate-800 disabled:opacity-50"
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

      <div
        className={`mt-4 grid gap-5 ${history ? "lg:grid-cols-[minmax(0,1fr)_340px]" : ""}`}
      >
        <div className="overflow-x-auto rounded-2xl border border-slate-800 bg-slate-950/60 backdrop-blur-xl">
          {loading ? (
            <div className="flex min-h-40 items-center justify-center">
              <Loader2 className="h-6 w-6 animate-spin text-cyan-400" />
            </div>
          ) : users.length === 0 ? (
            <p className="px-5 py-10 text-center text-sm text-slate-500">
              No users match this search.
            </p>
          ) : (
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-800 text-[10px] uppercase tracking-wider text-slate-500">
                  <th className="px-5 py-3 font-semibold">User</th>
                  <th className="px-3 py-3 font-semibold">Role</th>
                  <th className="px-3 py-3 font-semibold">Status</th>
                  <th className="px-3 py-3 font-semibold">Bookings</th>
                  <th className="px-3 py-3 font-semibold">Joined</th>
                  <th className="px-3 py-3 text-right font-semibold">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr
                    key={user.userId}
                    className="border-b border-slate-800/60 hover:bg-slate-900/40"
                  >
                    <td className="px-5 py-3">
                      <div className="font-semibold text-white">
                        {user.fullName}
                      </div>
                      <div className="text-[11px] text-slate-500">
                        {user.email}
                        {user.phoneNumber && ` · ${user.phoneNumber}`}
                      </div>
                    </td>
                    <td className="px-3 py-3">
                      <span
                        className={`inline-flex rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider ${roleBadge(
                          user.role
                        )}`}
                      >
                        {(user.role || "ROLE_USER").replace("ROLE_", "")}
                      </span>
                    </td>
                    <td className="px-3 py-3">
                      <span
                        className={`inline-flex rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider ${statusBadge(
                          user.status
                        )}`}
                      >
                        {user.status}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-slate-300">
                      {user.bookingCount}
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 text-slate-500">
                      {when(user.createdAt)}
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 text-right">
                      <div className="inline-flex gap-2">
                        <button
                          type="button"
                          onClick={() => openHistory(user)}
                          disabled={historyLoading}
                          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 px-2.5 py-1.5 text-[11px] font-semibold text-slate-300 hover:bg-slate-800 disabled:opacity-50"
                        >
                          <Eye className="h-3.5 w-3.5" />
                          History
                        </button>
                        {user.status === "SUSPENDED" ? (
                          <button
                            type="button"
                            onClick={() => reactivate(user)}
                            disabled={busy}
                            className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-500/30 px-2.5 py-1.5 text-[11px] font-semibold text-emerald-300 hover:bg-emerald-500/10 disabled:opacity-50"
                          >
                            <UserCheck className="h-3.5 w-3.5" />
                            Reactivate
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => {
                              setSuspendTarget(user);
                              setReason("");
                              setNotice("");
                            }}
                            disabled={busy}
                            className="inline-flex items-center gap-1.5 rounded-lg border border-rose-500/30 px-2.5 py-1.5 text-[11px] font-semibold text-rose-300 hover:bg-rose-500/10 disabled:opacity-50"
                          >
                            <Ban className="h-3.5 w-3.5" />
                            Suspend
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        {history && (
          <aside className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5 backdrop-blur-xl">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h3 className="text-sm font-semibold text-white">
                  {history.user.fullName}
                </h3>
                <p className="text-xs text-slate-500">
                  {history.user.email}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setHistory(null)}
                className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-800 hover:text-white"
                aria-label="Close history"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <h4 className="mt-4 text-[11px] font-semibold uppercase tracking-wider text-slate-500">
              Booking history
            </h4>

            {historyLoading ? (
              <div className="flex min-h-24 items-center justify-center">
                <Loader2 className="h-5 w-5 animate-spin text-cyan-400" />
              </div>
            ) : (history.bookings || []).length === 0 ? (
              <p className="mt-3 text-xs text-slate-500">
                No bookings yet.
              </p>
            ) : (
              <ul className="mt-3 space-y-2">
                {(history.bookings || []).map((booking) => (
                  <li
                    key={booking.bookingId}
                    className="rounded-xl border border-slate-800 p-3"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono text-[11px] text-slate-400">
                        {booking.bookingReference}
                      </span>
                      <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
                        {booking.bookingType}
                      </span>
                    </div>
                    <div className="mt-1 flex items-center justify-between text-xs">
                      <span className="font-semibold text-white">
                        {booking.currency}{" "}
                        {Number(booking.totalAmount || 0).toFixed(2)}
                      </span>
                      <span className="text-slate-500">
                        {when(booking.createdAt)}
                      </span>
                    </div>
                    <div className="mt-1.5 flex gap-2 text-[10px] uppercase tracking-wider">
                      <span
                        className={
                          booking.paymentStatus === "PAID"
                            ? "text-emerald-400"
                            : "text-amber-400"
                        }
                      >
                        {booking.paymentStatus}
                      </span>
                      <span className="text-slate-500">
                        {booking.bookingStatus}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </aside>
        )}
      </div>
      {suspendTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4">
          <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-900 p-6">
            <div className="flex items-center gap-2 text-rose-300">
              <Ban className="h-5 w-5" />
              <h3 className="text-sm font-semibold uppercase tracking-wider">
                Suspend account
              </h3>
            </div>
            <p className="mt-2 text-sm text-slate-400">
              {suspendTarget.fullName} ({suspendTarget.email}) will be
              blocked from logging in and from refreshing any session
              they already hold.
            </p>

            <label className="mt-4 block text-[11px] font-semibold uppercase tracking-wider text-slate-500">
              Reason (required)
              <textarea
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                maxLength={500}
                rows={3}
                placeholder="Explain why this account is being suspended."
                className="mt-2 w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2.5 text-xs normal-case tracking-normal text-white outline-none placeholder:text-slate-600 focus:border-rose-400"
              />
            </label>

            <div className="mt-5 flex gap-3">
              <button
                type="button"
                onClick={confirmSuspend}
                disabled={busy || !reason.trim()}
                className="inline-flex items-center gap-2 rounded-xl bg-rose-500 px-4 py-2.5 text-sm font-semibold text-white hover:bg-rose-400 disabled:opacity-50"
              >
                {busy ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <XCircle className="h-4 w-4" />
                )}
                Suspend
              </button>
              <button
                type="button"
                onClick={() => setSuspendTarget(null)}
                disabled={busy}
                className="rounded-xl border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-300 hover:bg-slate-800"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {!loading && !error && users.length > 0 && (
        <p className="mt-3 flex items-center gap-1.5 text-[11px] text-slate-500">
          <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400" />
          {users.length} account{users.length === 1 ? "" : "s"} listed.
        </p>
      )}
    </section>
  );
}