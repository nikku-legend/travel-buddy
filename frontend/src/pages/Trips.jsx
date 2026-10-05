import { useCallback, useEffect, useState } from "react";
import {
  AlertTriangle,
  CalendarDays,
  Loader2,
  MapPin,
  Plus,
} from "lucide-react";
import { Link } from "react-router-dom";

import tripService from "../services/tripService";
import { useAuth } from "../context/useAuth";

/*
 * My trips. (SRS 2.2)
 *
 * The list is where a traveller re-enters a journey, so it shows
 * the same checkpoint count the map shows. A trip that has three
 * of six checkpoints behind it should not look identical to one
 * that has never been paid for.
 */

const STATUS_TONE = {
  DRAFT: "bg-white/10 text-white/70",
  PLANNING: "bg-sky-500/20 text-sky-200",
  READY_FOR_CHECKOUT: "bg-amber-500/20 text-amber-200",
  CONFIRMED: "bg-emerald-500/20 text-emerald-200",
  IN_PROGRESS: "bg-indigo-500/20 text-indigo-200",
  COMPLETED: "bg-emerald-500/20 text-emerald-200",
  REVIEW_OPEN: "bg-purple-500/20 text-purple-200",
  CLOSED: "bg-white/10 text-white/50",
};

function reachedOf(trip) {
  const milestones = trip.milestones ?? [];
  return milestones.filter((m) => m.completed).length;
}

function Trips() {
  const { user } = useAuth();

  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const data = await tripService.getTrips();
      setTrips(Array.isArray(data) ? data : []);
    } catch (err) {
      const status = err?.response?.status;

      if (status === 401 || status === 403) {
        setError("Please log in to view your trips.");
      } else {
        setError(
          err?.response?.data?.message ||
            "Unable to load your trips."
        );
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-amber-300" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <div className="mb-8 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-white sm:text-3xl">
          My trips
        </h1>

        {user && (
          <Link
            to="/trips/new"
            className="inline-flex items-center gap-2 rounded-xl bg-amber-400 px-4 py-2 text-sm font-semibold text-slate-900 hover:bg-amber-300"
          >
            <Plus className="h-4 w-4" />
            Plan a trip
          </Link>
        )}
      </div>

      {error && (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-amber-400/30 bg-amber-400/10 px-4 py-3 text-sm text-amber-200">
          <AlertTriangle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      {!error && trips.length === 0 && (
        <div className="rounded-2xl border border-dashed border-white/15 p-12 text-center">
          <MapPin className="mx-auto h-8 w-8 text-white/30" />
          <p className="mt-4 text-white/70">
            No trips yet.
          </p>
          <p className="mt-1 text-sm text-white/40">
            Plan one and your route and checkpoints
            will appear here.
          </p>
        </div>
      )}

      <ul className="grid gap-4 sm:grid-cols-2">
        {trips.map((trip) => {
          const total = (trip.milestones ?? []).length;
          const reached = reachedOf(trip);

          return (
            <li key={trip.tripId}>
              <Link
                to={`/trips/${trip.tripId}`}
                className="block h-full rounded-2xl border border-white/10 bg-white/5 p-5 transition hover:border-amber-300/40 hover:bg-white/10"
              >
                <div className="flex items-start justify-between gap-3">
                  <h2 className="font-semibold text-white">
                    {trip.title}
                  </h2>

                  <span
                    className={[
                      "shrink-0 rounded-full px-2.5 py-0.5",
                      "text-[10px] font-semibold uppercase",
                      "tracking-wider",
                      STATUS_TONE[trip.status] ??
                        "bg-white/10 text-white/60",
                    ].join(" ")}
                  >
                    {trip.status}
                  </span>
                </div>

                <p className="mt-2 inline-flex items-center gap-2 text-sm text-white/60">
                  <CalendarDays className="h-4 w-4" />
                  {trip.startDate} to {trip.endDate}
                </p>

                {/*
                 * Only when there are checkpoints to count. A
                 * "0 of 0 reached" reads as failure on a trip
                 * that simply has no route yet.
                 */}
                {total > 0 && (
                  <p className="mt-3 text-xs text-white/40">
                    {reached} of {total} checkpoints
                    reached
                  </p>
                )}
              </Link>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

export default Trips;