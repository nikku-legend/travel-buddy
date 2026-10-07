import {
  AlertTriangle,
  CalendarCheck,
  Loader2,
  RefreshCw,
  TrendingUp,
  Users,
  Wallet,
} from "lucide-react";

/*
 * OVERVIEW & ANALYTICS  (FR-30)
 *
 * Every tile renders a fact from AdminStatsResponse. The old
 * console displayed hard-coded "Active", "₹0.00" and "Normal"
 * regardless of platform contents; nothing here is literal.
 */

function money(amount, currency) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: currency || "INR",
    maximumFractionDigits: 0,
  }).format(Number(amount || 0));
}

function Tile({ label, value, sub, icon, tone }) {
  const tones = {
    blue: "bg-blue-500/10 text-blue-400 border-blue-500/20",
    amber: "bg-amber-500/10 text-amber-400 border-amber-500/20",
    emerald:
      "bg-emerald-500/10 text-emerald-400 border-emerald-500/20",
    rose: "bg-rose-500/10 text-rose-400 border-rose-500/20",
    purple:
      "bg-purple-500/10 text-purple-400 border-purple-500/20",
  };

  return (
    <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5 backdrop-blur-xl">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
          {label}
        </span>
        <div
          className={`flex h-9 w-9 items-center justify-center rounded-xl border ${tones[tone]}`}
        >
          {icon}
        </div>
      </div>
      <div className="mt-3 text-2xl font-bold text-white">{value}</div>
      <div className="mt-1 text-xs text-slate-500">{sub}</div>
    </div>
  );
}

function statusPill(status) {
  if (status === "PAID")
    return "border-emerald-500/30 bg-emerald-500/10 text-emerald-300";
  if (status === "REFUNDED")
    return "border-amber-500/30 bg-amber-500/10 text-amber-300";
  return "border-rose-500/30 bg-rose-500/10 text-rose-300";
}

export default function OverviewTab({ stats, loading, error, onRetry }) {
  if (loading) {
    return (
      <div className="flex min-h-40 items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-cyan-400" />
      </div>
    );
  }

  if (error || !stats) {
    return (
      <div className="flex min-h-40 flex-col items-center justify-center gap-3 rounded-2xl border border-slate-800 bg-slate-950/60 px-5 py-8 text-center">
        <AlertTriangle className="h-8 w-8 text-amber-400" />
        <p className="text-sm text-slate-400">
          {error || "Statistics are unavailable right now."}
        </p>
        <button
          type="button"
          onClick={onRetry}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-700 px-4 py-2 text-xs font-semibold text-slate-300 hover:bg-slate-800"
        >
          <RefreshCw className="h-3.5 w-3.5" />
          Retry
        </button>
      </div>
    );
  }

  const pending = stats.pendingApprovals || {};
  const alertCount = stats.openDisputes + stats.refundsPending;

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Tile
          label="Registered Users"
          value={stats.totalUsers.toLocaleString()}
          sub={`${stats.activeUsers} active · ${stats.suspendedUsers} suspended`}
          icon={<Users className="h-5 w-5" />}
          tone="blue"
        />
        <Tile
          label="Bookings"
          value={stats.totalBookings.toLocaleString()}
          sub={`${stats.confirmedBookings} confirmed · ${stats.cancelledBookings} cancelled`}
          icon={<CalendarCheck className="h-5 w-5" />}
          tone="purple"
        />
        <Tile
          label="Gross Revenue (paid)"
          value={money(stats.grossRevenue)}
          sub={`${money(stats.refundedRevenue)} refunded to date`}
          icon={<Wallet className="h-5 w-5" />}
          tone="emerald"
        />
        <Tile
          label="Action Required"
          value={alertCount.toLocaleString()}
          sub={`${stats.openDisputes} open disputes · ${stats.refundsPending} refunds pending`}
          icon={<TrendingUp className="h-5 w-5" />}
          tone={alertCount > 0 ? "rose" : "emerald"}
        />
      </div>

      <div className="flex flex-wrap gap-3">
        {[
          {
            label: "Partner applications",
            count: pending.partnerApplications,
          },
          { label: "Properties awaiting review", count: pending.properties },
          { label: "Reviews to moderate", count: pending.reviews },
        ].map((queue) => (
          <div
            key={queue.label}
            className={`inline-flex items-center gap-2 rounded-xl border px-4 py-2.5 text-xs font-semibold ${
              queue.count > 0
                ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
                : "border-slate-700 bg-slate-900 text-slate-400"
            }`}
          >
            <AlertTriangle className="h-3.5 w-3.5" />
            {queue.count > 0
              ? `${queue.count} ${queue.label}`
              : `No ${queue.label.toLowerCase()}`}
          </div>
        ))}
      </div>

      <div className="rounded-2xl border border-slate-800 bg-slate-950/60 backdrop-blur-xl">
        <div className="border-b border-slate-800 px-5 py-4">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-400">
            Recent Paid Sales
          </h2>
        </div>
        {(stats.recentSales || []).length === 0 ? (
          <p className="px-5 py-8 text-center text-sm text-slate-500">
            No paid bookings yet.
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
                  <th className="px-3 py-3 font-semibold">When</th>
                </tr>
              </thead>
              <tbody>
                {(stats.recentSales || []).map((sale) => (
                  <tr
                    key={sale.bookingId}
                    className="border-b border-slate-800/60 hover:bg-slate-900/40"
                  >
                    <td className="whitespace-nowrap px-5 py-3 font-mono text-slate-300">
                      {sale.bookingReference}
                    </td>
                    <td className="px-3 py-3 text-white">
                      {sale.guestName}
                    </td>
                    <td className="px-3 py-3 text-slate-400">
                      {sale.bookingType}
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 font-semibold text-white">
                      {money(sale.totalAmount, sale.currency)}
                    </td>
                    <td className="px-3 py-3">
                      <span
                        className={`inline-flex rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider ${statusPill(
                          sale.paymentStatus
                        )}`}
                      >
                        {sale.paymentStatus}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 text-slate-500">
                      {sale.createdAt
                        ? new Date(sale.createdAt).toLocaleString()
                        : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
