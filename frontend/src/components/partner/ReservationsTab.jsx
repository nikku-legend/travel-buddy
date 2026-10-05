import { useState } from "react";
import { AlertTriangle, DoorOpen } from "lucide-react";

import hotelPartnerService from "../../services/hotelPartnerService";
import RoomPicker from "./RoomPicker";

/*
 * Confirmed arrivals, and the room each one is waiting for. (FR-22)
 *
 * "Rooms still owed" is shown as a number rather than a yes/no: a
 * booking of three rooms with one assigned needs two more, and
 * "needs a room" would not tell the front desk how much work is
 * outstanding.
 *
 * Check-in is only offered when the backend says it is permitted.
 * The alternative is a button that fails on click, which teaches the
 * front desk the screen is unreliable rather than that the booking
 * is unpaid.
 */

function money(amount, currency) {
  if (amount === null || amount === undefined) return null;
  return `${currency ?? "INR"} ${Number(amount).toFixed(2)}`;
}

export default function ReservationsTab({
  reservations,
  busyId,
  onAct,
}) {
  const [picking, setPicking] = useState(null);

  if (reservations.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
        <DoorOpen className="mx-auto h-10 w-10 text-slate-400" />
        <h3 className="mt-3 text-sm font-semibold text-slate-900">
          No open reservations
        </h3>
        <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
          Confirmed bookings appear here the moment a traveller pays.
          Cancelled and completed stays are removed automatically.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {reservations.map((r) => (
        <article
          key={r.bookingId}
          className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
        >
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h3 className="text-base font-semibold text-slate-900">
                {r.guestName || "Guest"}
              </h3>
              <p className="mt-1 text-sm text-slate-500">
                {r.propertyName} · {r.roomCategory}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {r.checkIn} → {r.checkOut} · {r.nights} night
                {r.nights === 1 ? "" : "s"} · {r.guestCount} guest
                {r.guestCount === 1 ? "" : "s"}
                {r.guestPhone ? ` · ${r.guestPhone}` : ""}
              </p>
            </div>

            <div className="text-right">
              <p className="text-sm font-semibold text-slate-900">
                {money(r.totalAmount, r.currency)}
              </p>
              <p className="text-xs text-slate-500">{r.paymentStatus}</p>
              {r.voucherCode && (
                <p className="mt-1 font-mono text-xs text-slate-600">
                  {r.voucherCode}
                </p>
              )}
            </div>
          </div>

          {r.specialRequests && (
            <p className="mt-3 rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
              <span className="font-semibold">Guest note:</span>{" "}
              {r.specialRequests}
            </p>
          )}

          <div className="mt-4 rounded-xl border border-slate-200 p-4">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Rooms
            </p>

            <p className="mt-2 text-sm text-slate-900">
              {r.roomsAssigned} of {r.roomsBooked} assigned
              {r.assignedRoomNumbers ? ` — ${r.assignedRoomNumbers}` : ""}
            </p>

            {r.roomsStillToAssign > 0 && (
              <p className="mt-1 inline-flex items-center gap-2 text-sm font-medium text-amber-700">
                <AlertTriangle className="h-4 w-4" />
                {r.roomsStillToAssign} room
                {r.roomsStillToAssign === 1 ? "" : "s"} still owed to this
                guest
              </p>
            )}

            {r.canAssign &&
              (picking === r.bookingId ? (
                <RoomPicker
                  reservation={r}
                  busy={busyId === `assign-${r.bookingId}`}
                  onAssign={(roomId) =>
                    onAct(`assign-${r.bookingId}`, async () => {
                      await hotelPartnerService.assignRoom(
                        r.bookingId,
                        roomId
                      );
                      setPicking(null);
                    })
                  }
                />
              ) : (
                <button
                  type="button"
                  onClick={() => setPicking(r.bookingId)}
                  className="mt-3 rounded-lg border border-slate-900 px-4 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-900 hover:text-white"
                >
                  Assign room
                </button>
              ))}
          </div>

          {r.paymentStatus !== "PAID" && (
            <p className="mt-3 text-xs text-amber-700">
              Not paid yet, so this guest cannot be checked in.
            </p>
          )}
        </article>
      ))}
    </div>
  );
}
