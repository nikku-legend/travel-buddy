import { useCallback, useEffect, useState } from "react";
import {
  AlertTriangle,
  Ban,
  CalendarDays,
  DoorClosed,
  Loader2,
} from "lucide-react";

import hotelPartnerService from "../../services/hotelPartnerService";

/*
 * Nightly inventory for one room type. (FR-21)
 *
 * Shows all three quantities — booked, blocked and free — rather than
 * just colouring a date red. A partner asking "why is this night
 * full?" needs the reason, and "blocked for maintenance" and "three
 * guests booked" are very different answers.
 *
 * A night with no inventory row at all is rendered as "no rows"
 * rather than as zero available. The difference matters: zero
 * available means nobody may book, while no row means nobody has
 * priced that night. Both are unbookable, but only one is a
 * configuration problem.
 */

export function isoToday() {
  return new Date().toISOString().slice(0, 10);
}

export function addDays(iso, count) {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + count);
  return d.toISOString().slice(0, 10);
}

export default function InventoryTab({ properties }) {
  const bookable = properties.filter((p) => p.isLive);

  const [propertyId, setPropertyId] = useState(null);
  const [rooms, setRooms] = useState([]);
  const [roomTypeId, setRoomTypeId] = useState(null);
  const [nights, setNights] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (propertyId === null && bookable.length > 0) {
      setPropertyId(bookable[0].propertyId);
    }
  }, [propertyId, bookable]);

  const loadRooms = useCallback(async () => {
    if (propertyId === null) return;

    setLoading(true);
    setError("");

    try {
      const rows = await hotelPartnerService.getRoomTypes(propertyId);
      setRooms(rows);

      setRoomTypeId((current) =>
        rows.length === 0
          ? null
          : rows.some((r) => r.roomTypeId === current)
            ? current
            : rows[0].roomTypeId
      );
    } catch (err) {
      setError(
        err?.response?.data?.message || "Unable to load your rooms."
      );
    } finally {
      setLoading(false);
    }
  }, [propertyId]);

  useEffect(() => {
    loadRooms();
  }, [loadRooms]);

  const loadNights = useCallback(async () => {
    if (propertyId === null || roomTypeId === null) return;

    try {
      setNights(
        await hotelPartnerService.getInventoryCalendar(
          propertyId,
          roomTypeId,
          isoToday(),
          addDays(isoToday(), 13)
        )
      );
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Unable to load the inventory calendar."
      );
    }
  }, [propertyId, roomTypeId]);

  useEffect(() => {
    loadNights();
  }, [loadNights]);

  if (bookable.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
        <DoorClosed className="mx-auto h-10 w-10 text-slate-400" />
        <h3 className="mt-3 text-sm font-semibold text-slate-900">
          No live property yet
        </h3>
        <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
          Inventory is only editable once a property is approved and
          visible to travellers. A draft has nothing to sell.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {error && (
        <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <div className="flex flex-wrap gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-xs font-semibold uppercase tracking-wider text-slate-500">
            Property
          </span>
          <select
            value={propertyId ?? ""}
            onChange={(e) => setPropertyId(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          >
            {bookable.map((p) => (
              <option key={p.propertyId} value={p.propertyId}>
                {p.name}
              </option>
            ))}
          </select>
        </label>

        <label className="text-sm">
          <span className="mb-1 block text-xs font-semibold uppercase tracking-wider text-slate-500">
            Room type
          </span>
          <select
            value={roomTypeId ?? ""}
            disabled={rooms.length === 0}
            onChange={(e) => setRoomTypeId(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm disabled:opacity-50"
          >
            {rooms.map((r) => (
              <option key={r.roomTypeId} value={r.roomTypeId}>
                {r.categoryName}
              </option>
            ))}
          </select>
        </label>
      </div>

      {loading && (
        <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
      )}

      {rooms.length > 0 && (
        <div className="flex flex-wrap gap-3">
          {rooms.map((r) => (
            <div
              key={r.roomTypeId}
              className="rounded-xl border border-slate-200 bg-white px-4 py-3"
            >
              <p className="text-sm font-semibold text-slate-900">
                {r.categoryName}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {r.totalInventory} rooms · sleeps {r.maxOccupancy}
              </p>
              {!r.hasAvailability && (
                <p className="mt-1 text-xs font-medium text-red-700">
                  No inventory generated — not bookable
                </p>
              )}
            </div>
          ))}
        </div>
      )}

      {roomTypeId !== null && (
        <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="inline-flex items-center gap-2 text-sm font-bold uppercase tracking-wider text-slate-900">
            <CalendarDays className="h-4 w-4" />
            Next 14 nights
          </h2>

          {nights.length === 0 ? (
            <p className="mt-3 text-sm text-slate-600">
              No inventory rows for this room type, so nothing can be
              booked until nights are generated.
            </p>
          ) : (
            <div className="mt-4 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-xs uppercase tracking-wider text-slate-400">
                    <th className="py-2 pr-3 font-semibold">Night</th>
                    <th className="py-2 pr-3 font-semibold">Total</th>
                    <th className="py-2 pr-3 font-semibold">Booked</th>
                    <th className="py-2 pr-3 font-semibold">Blocked</th>
                    <th className="py-2 font-semibold">Free</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {nights.map((n) => (
                    <tr key={n.date}>
                      <td className="py-2 pr-3 text-slate-900">
                        {n.date}
                      </td>
                      <td className="py-2 pr-3 text-slate-600">
                        {n.total}
                      </td>
                      <td className="py-2 pr-3 text-slate-600">
                        {n.reserved}
                      </td>
                      <td className="py-2 pr-3 text-slate-600">
                        {n.blocked > 0 ? (
                          <span className="inline-flex items-center gap-1">
                            <Ban className="h-3 w-3 text-amber-600" />
                            {n.blocked}
                            {n.blockReason ? (
                              <span className="text-xs text-slate-500">
                                {String(n.blockReason)
                                  .replace(/_/g, " ")
                                  .toLowerCase()}
                              </span>
                            ) : null}
                          </span>
                        ) : (
                          "0"
                        )}
                      </td>
                      <td
                        className={`py-2 font-semibold ${
                          n.available > 0
                            ? "text-emerald-700"
                            : "text-slate-400"
                        }`}
                      >
                        {n.available}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
