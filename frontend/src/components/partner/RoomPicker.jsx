import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";

import hotelPartnerService from "../../services/hotelPartnerService";

/*
 * Room picker for one reservation. (FR-22)
 *
 * Mounted only when the front desk actually asks for it, so a hotel
 * with a hundred arrivals does not fire a hundred room lookups on
 * page load.
 *
 * Occupied rooms are shown but disabled, with the reason. Hiding them
 * would leave a partner wondering why room 104 is not offered when
 * the guest in it is due to leave tomorrow morning.
 */
export default function RoomPicker({ reservation, onAssign, busy }) {
  const [rooms, setRooms] = useState(null);
  const [chosen, setChosen] = useState("");

  useEffect(() => {
    let live = true;

    hotelPartnerService
      .getPhysicalRooms(reservation.propertyId, {
        from: reservation.checkIn,
        to: reservation.checkOut,
      })
      .then((rows) => {
        if (live) setRooms(rows);
      })
      .catch(() => {
        if (live) setRooms([]);
      });

    return () => {
      live = false;
    };
  }, [reservation.propertyId, reservation.checkIn, reservation.checkOut]);

  if (rooms === null) {
    return (
      <div className="mt-3 flex items-center gap-2 text-sm text-slate-500">
        <Loader2 className="h-4 w-4 animate-spin" />
        Loading your rooms…
      </div>
    );
  }

  if (rooms.length === 0) {
    return (
      <p className="mt-3 text-sm text-amber-700">
        You have no numbered rooms for this property yet. Add rooms to
        the room type before assigning.
      </p>
    );
  }

  return (
    <div className="mt-3 flex flex-wrap items-center gap-3">
      <select
        value={chosen}
        onChange={(e) => setChosen(e.target.value)}
        className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
      >
        <option value="">Choose a room number…</option>

        {rooms.map((room) => (
          <option
            key={room.physicalRoomId}
            value={room.physicalRoomId}
            disabled={!room.assignable}
          >
            {room.roomNumber}
            {room.floorLabel ? ` · ${room.floorLabel}` : ""}
            {room.assignable ? "" : " — occupied for these dates"}
          </option>
        ))}
      </select>

      <button
        type="button"
        disabled={!chosen || busy}
        onClick={() => onAssign(Number(chosen))}
        className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-40"
      >
        {busy ? (
          <Loader2 className="h-4 w-4 animate-spin" />
        ) : (
          "Assign room"
        )}
      </button>
    </div>
  );
}
