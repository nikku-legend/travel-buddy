import { AlertTriangle, BedDouble } from "lucide-react";

import hotelPartnerService from "../../services/hotelPartnerService";

/*
 * In-house guests. (FR-23)
 *
 * Every button here is rendered from a backend permission flag.
 * Check-out is offered only once a guest is actually checked in, and
 * no-show only while they are still assigned but not arrived — which
 * is what those actions mean. Offering either at the wrong moment
 * invites the front desk to guess.
 */
export default function FrontDeskTab({ stays, onAct, busyId }) {
  if (!stays || stays.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
        <BedDouble className="mx-auto h-10 w-10 text-slate-400" />
        <h3 className="mt-3 text-sm font-semibold text-slate-900">
          Nobody is in house
        </h3>
        <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
          Guests appear here once they have a room number and an
          arrival date. Check them in from the Reservations tab.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {stays.map((s) => (
        <article
          key={s.stayId}
          className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
        >
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h3 className="text-base font-semibold text-slate-900">
                Room {s.roomNumber}
                {s.floorLabel ? ` · ${s.floorLabel}` : ""}
              </h3>
              <p className="mt-1 text-sm text-slate-500">
                {s.guestName || "Guest"} · {s.categoryName}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {s.checkInDate} → {s.checkOutDate} ·{" "}
                {s.bookingReference}
              </p>
            </div>

            <span
              className={`rounded-full px-3 py-1 text-xs font-semibold ${
                s.status === "CHECKED_IN"
                  ? "bg-emerald-100 text-emerald-800"
                  : "bg-slate-100 text-slate-700"
              }`}
            >
              {String(s.status).replace(/_/g, " ")}
            </span>
          </div>

          <div className="mt-4 flex flex-wrap gap-3">
            {s.canCheckIn && (
              <button
                type="button"
                disabled={busyId === `in-${s.stayId}`}
                onClick={() =>
                  onAct(`in-${s.stayId}`, () =>
                    hotelPartnerService.checkIn(s.stayId)
                  )
                }
                className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-40"
              >
                Check in
              </button>
            )}

            {s.canCheckOut && (
              <button
                type="button"
                disabled={busyId === `out-${s.stayId}`}
                onClick={() =>
                  onAct(`out-${s.stayId}`, () =>
                    hotelPartnerService.checkOut(s.stayId)
                  )
                }
                className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-40"
              >
                Check out
              </button>
            )}

            {s.status === "ASSIGNED" && (
              <button
                type="button"
                onClick={() =>
                  onAct(`ns-${s.stayId}`, () =>
                    hotelPartnerService.markNoShow(s.stayId)
                  )
                }
                className="rounded-lg border border-amber-300 px-4 py-2 text-sm font-semibold text-amber-800 hover:bg-amber-50"
              >
                Mark no-show
              </button>
            )}
          </div>

          {!s.canCheckIn && s.status === "ASSIGNED" && (
            <p className="mt-3 inline-flex items-center gap-2 text-xs text-amber-700">
              <AlertTriangle className="h-3.5 w-3.5" />
              {s.blockedReason
                ? s.blockedReason
                : "Not ready to check in yet."}
            </p>
          )}
        </article>
      ))}
    </div>
  );
}
